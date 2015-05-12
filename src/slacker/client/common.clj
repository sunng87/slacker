(ns slacker.client.common
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common protocol])
  (:use [link.core])
  (:use [link.tcp])
  (:require [clojure.tools.logging :as log]
            [link.codec :refer [netty-encoder netty-decoder]]
            [link.ssl :refer [ssl-handler-from-jdk-ssl-context]])
  (:import [java.net ConnectException InetSocketAddress InetAddress]
           [java.nio.channels ClosedChannelException]
           [java.util.concurrent ScheduledThreadPoolExecutor
            TimeUnit ScheduledFuture]
           [clojure.lang IDeref IBlockingDeref IPending]))

(defn- handle-valid-response [response]
  (let [[content-type code data] (second response)]
    (case code
      :success {:result (deserialize content-type data)}
      :not-found {:cause {:error code}}
      :exception {:cause {:error code
                          :exception (deserialize content-type data)}}
      {:cause {:error :invalid-result-code}})))

(defn make-request [tid content-type func-name params]
  (let [serialized-params (serialize content-type params)]
    [version tid [:type-request [content-type func-name serialized-params]]]))

(def ping-packet [version 0 [:type-ping]])

(defn make-inspect-request [tid cmd args]
  [version tid [:type-inspect-req
                [cmd (serialize :clj args :string)]]])

(defn parse-inspect-response [response]
  {:result (deserialize :clj (-> response
                                 second
                                 first)
                        :string)})

(defn handle-response [response]
  (case (first response)
    :type-response (handle-valid-response response)
    :type-inspect-ack (parse-inspect-response response)
    :type-pong (log/debug "pong")
    :type-error {:cause {:error (-> response second first)}}
    nil))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params options])
  (async-call-remote [this ns-name func-name params cb options])
  (inspect [this cmd args])
  (server-addr [this])
  (ping [this])
  (close [this]))

(defprotocol KeepAliveClientProtocol
  (schedule-ping [this ping-interval])
  (cancel-ping [this]))

(defprotocol SlackerClientFactoryProtocol
  (schedule-task [this task delay] [this task delay interval])
  (shutdown [this])
  (get-state [this addr])
  (get-states [this])
  (open-tcp-client [this host port])
  (assoc-client! [this client])
  (dissoc-client! [this client]))

(deftype DefaultSlackerClientFactory [tcp-factory schedule-pool states]
  SlackerClientFactoryProtocol
  (schedule-task [this task delay]
    (.schedule ^ScheduledThreadPoolExecutor schedule-pool
               ^Runnable (cast Runnable
                               #(try
                                  (task)
                                  (catch Exception e nil)))
               (long delay)
               TimeUnit/MILLISECONDS))
  (schedule-task [this task delay interval]
    (.scheduleAtFixedRate ^ScheduledThreadPoolExecutor schedule-pool
                          #(try
                             (task)
                             (catch Exception e nil))
                          (long delay)
                          (long interval)
                          TimeUnit/MILLISECONDS))
  (shutdown [this]
    ;; shutdown associated clients
    (doseq [a (map :refs (vals @states))]
      (doseq [c (flatten a)]
        (close c)))
    (stop-clients tcp-factory)
    (.shutdown ^ScheduledThreadPoolExecutor schedule-pool))
  (get-state [this addr]
    (@states addr))
  (get-states [this]
    @states)
  (open-tcp-client [this host port]
    (tcp-client tcp-factory host port :lazy-connect true))
  (assoc-client! [this client]
    (swap! states
           (fn [snapshot]
             (let [addr (server-addr client)]
               (if (snapshot addr)
                 (update-in snapshot [addr :refs] conj client)
                 (assoc snapshot addr
                        {:pendings (atom {})
                         :idgen (atom 0)
                         :keep-alive (atom {})
                         :refs [client]}))))))
  (dissoc-client! [this client]
    (swap! states
           (fn [snapshot]
             (let [addr (server-addr client)
                   refs (remove #(= client %)
                                (-> snapshot (get addr) :refs))]
               (if (empty? refs)
                 (dissoc snapshot addr)
                 (assoc snapshot :refs refs)))))))

(defn- channel-hostport [ch]
  (let [addr (link.core/remote-addr ch)]
    (str (.getHostAddress ^InetAddress
                          (.getAddress ^InetSocketAddress addr))
         ":" (.getPort ^InetSocketAddress addr))))

(defn- next-trans-id [trans-id-gen]
  (swap! trans-id-gen unchecked-inc))

(deftype SlackerClient [addr conn factory content-type options]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params call-options]
    (let [state (get-state factory (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! (:pendings state) assoc tid {:promise prms})
      (send! conn request)
      (deref prms (or (:timeout call-options) (:timeout options) *timeout*) nil)
      (if (realized? prms)
        @prms
        (do
          (swap! (:pendings state) dissoc tid)
          {:cause {:error :timeout}}))))
  (async-call-remote [this ns-name func-name params sys-cb call-options]
    (let [state (get-state factory (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          request (make-request tid content-type fname params)
          prms (promise)
          timeout-check (fn []
                          (when-let [handler (get @(:pendings state) tid)]
                            (swap! (:pendings state) dissoc tid)
                            (let [result {:cause {:error :timeout}}]
                              (deliver (:promise handler) result)
                              (when-let [cb (:callback handler)]
                                (cb result)))))]
      (swap! (:pendings state) assoc
             tid {:promise prms :callback sys-cb :async? true})
      (send! conn request)
      (schedule-task factory timeout-check
                     (or (:timeout call-options) (:timeout options) *timeout*))
      prms))
  (inspect [this cmd args]
    (let [state (get-state factory (server-addr this))
          tid (next-trans-id (:idgen state))
          request (make-inspect-request tid cmd args)
          prms (promise)]
      (swap! (:pendings state) assoc tid {:promise prms :type :inspect})
      (send! conn request)
      (deref prms (or (:timeout options) *timeout*) nil)
      (if (realized? prms)
        @prms
        (do
          (swap! (:pendings state) dissoc tid)
          {:cause {:error :timeout}}))))
  (ping [this]
    (send! conn ping-packet)
    (log/debug "ping"))
  (close [this]
    (cancel-ping this)
    (dissoc-client! factory this)
    (close! conn))
  (server-addr [this]
    addr)

  KeepAliveClientProtocol
  (schedule-ping [this interval]
    (let [cancelable (schedule-task factory #(ping this) 0 interval)
          state (get-state factory (server-addr this))]
      (swap! (:keep-alive state) assoc this cancelable)))
  (cancel-ping [this]
    (when-let [state (get-state factory (server-addr this))]
      (when-let [cancelable (@(:keep-alive state) this)]
        (.cancel ^ScheduledFuture cancelable true)))))

(defn- create-link-handler
  "The event handler for client"
  [server-requests]
  (create-handler
   (on-message [ch msg]
               (when-let [rmap (-> ch
                                   (channel-hostport)
                                   (@server-requests)
                                   :pendings)]
                 (let [tid (second msg)
                       msg-body (nth msg 2)
                       handler (get @rmap tid)]
                   (swap! rmap dissoc tid)
                   (let [result (handle-response msg-body)]
                     (when (not-empty handler)
                       (deliver (:promise handler) result)
                       (when (:async? handler)
                         (when-let [cb (:callback handler)]
                           (cb result)))
                       ;; pong
                       result)))))
   (on-error [ch ^Exception exc]
             (if (or
                  (instance? ConnectException exc)
                  (instance? ClosedChannelException exc))
               (log/warn "Failed to connect to server or connection lost.")
               (log/error exc "Unexpected error in event loop")))))

(def ^:dynamic *options*
  {:tcp-nodelay true
   :so-reuseaddr true
   :so-keepalive true
   :write-buffer-high-water-mark (int 0xFFFF) ; 65kB
   :write-buffer-low-water-mark (int 0xFFF) ; 4kB
   :connect-timeout-millis (int 5000)})

(defn create-client-factory [ssl-context]
  (let [server-requests (atom {})
        handler (create-link-handler server-requests)
        schedule-pool (ScheduledThreadPoolExecutor.
                       (.availableProcessors (Runtime/getRuntime)))
        ssl-handler (when ssl-context
                      (ssl-handler-from-jdk-ssl-context ssl-context true))
        handlers [(netty-encoder slacker-base-codec)
                  (netty-decoder slacker-base-codec)
                  handler]
        handlers (if ssl-handler
                   (conj (seq handlers) ssl-handler)
                   handlers)]
    (DefaultSlackerClientFactory.
      (tcp-client-factory handlers
                          :codec slacker-base-codec
                          :options *options*)
      schedule-pool server-requests)))

(defn host-port
  "get host and port from connection string"
  [connection-string]
  (let [[host port] (split connection-string #":")]
    [host (Integer/valueOf ^String port)]))

(defn create-client [slacker-client-factory addr content-type options]
  (let [[host port] (host-port addr)
        client (open-tcp-client slacker-client-factory host port)
        slacker-client (SlackerClient. addr
                                       client
                                       slacker-client-factory
                                       content-type
                                       options)]
    (assoc-client! slacker-client-factory slacker-client)
    (when-let [interval (:ping-interval options)]
      (schedule-ping slacker-client (* 1000 interval)))
    slacker-client))

(defn- parse-exception [einfo]
  (doto (Exception. ^String (:msg einfo))
    (.setStackTrace (:stacktrace einfo))))

(defn user-friendly-cause [call-result]
  (when (:cause call-result)
    (if (and (= :exception (-> call-result :cause :error))
             (map? (-> call-result :cause :exception)))
      (parse-exception (-> call-result :cause :exception))
      (:cause call-result))))

(defn process-call-result [call-result]
  (if (nil? (:cause call-result))
    (:result call-result)
    (let [e (user-friendly-cause call-result)]
      (if (instance? Throwable e)
        (throw (ex-info "Slacker client exception" {:error :exception} e))
        (throw (ex-info "Slacker client error" e))))))

(deftype ExceptionEnabledPromise [prms]
  IDeref
  (deref [_]
    (process-call-result @prms))
  IBlockingDeref
  (deref [_ timeout timeout-var]
    (deref prms timeout nil)
    (if (realized? prms)
      (deref _)
      timeout-var))
  IPending
  (isRealized [_]
    (realized? prms)))

(defn exception-enabled-promise
  ([] (exception-enabled-promise (promise)))
  ([prms] (ExceptionEnabledPromise. prms)))

(def ^:dynamic *sc* nil)
(defn invoke-slacker
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [sc remote-call-info
   & {:keys [async? callback]
      :or {async? false callback nil}
      :as options}]
  (let [sc @(or *sc* sc)  ;; allow local binding to override client
        [nsname fname args] remote-call-info]
    (if (or async? (not (nil? callback)))
      ;; async
      (let [sys-cb (fn [call-result]
                     (let [cb (or callback (constantly true))]
                       (cb (user-friendly-cause call-result)
                           (:result call-result))))]
        (exception-enabled-promise
         (async-call-remote sc nsname fname args sys-cb options)))

      ;; sync
      (process-call-result (sync-call-remote sc nsname fname args options)))))

(defn meta-remote
  "get metadata of a remote function by inspect api"
  [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (let [call-result (inspect @sc :meta fname)]
      (if (nil? (:cause call-result))
        (:result call-result)
        (throw (ex-info "Slacker client error"
                        (user-friendly-cause call-result)))))))

(defn functions-remote
  "get functions of a remote namespace"
  [sc n]
  (let [call-result (inspect @sc :functions n)]
    (if (nil? (:cause call-result))
      (:result call-result)
      (throw (ex-info "Slacker client error"
                      (user-friendly-cause call-result))))))

(defn shutdown-factory [factory]
  (shutdown factory))
