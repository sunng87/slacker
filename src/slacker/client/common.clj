(ns slacker.client.common
  (:refer-clojure :exclude [send])
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common protocol])
  (:use [link.core :exclude [close]])
  (:use [link.tcp])
  (:require [clojure.tools.logging :as log])
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
    (let [state (@(nth factory 2) (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! (:pendings state) assoc tid {:promise prms})
      (send conn request)
      (deref prms (or (:timeout options) *timeout*) nil)
      (if (realized? prms)
        @prms
        (do
          (swap! (:pendings state) dissoc tid)
          {:cause {:error :timeout}}))))
  (async-call-remote [this ns-name func-name params sys-cb call-options]
    (let [state (@(nth factory 2) (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! (:pendings state) assoc
             tid {:promise prms :callback sys-cb :async? true})
      (send conn request)
      prms))
  (inspect [this cmd args]
    (let [state (@(nth factory 2) (server-addr this))
          tid (next-trans-id (:idgen state))
          request (make-inspect-request tid cmd args)
          prms (promise)]
      (swap! (:pendings state) assoc tid {:promise prms :type :inspect})
      (send conn request)
      (deref prms (or (:timeout options) *timeout*) nil)
      (if (realized? prms)
        @prms
        (do
          (swap! (:pendings state) dissoc tid)
          {:cause {:error :timeout}}))))
  (ping [this]
    (send conn ping-packet)
    (log/debug "ping"))
  (close [this]
    (let [server-requests (nth factory 2)]
      (swap! server-requests
             (fn [snapshot]
               (let [addr (server-addr this)
                     refs (remove #(= this %)
                                  (-> snapshot (get addr) :refs))]
                 (if (empty? refs)
                   (dissoc snapshot addr)
                   (assoc snapshot :refs refs))))))
    (cancel-ping this)
    (link.core/close conn))
  (server-addr [this]
    addr)

  KeepAliveClientProtocol
  (schedule-ping [this interval]
    (let [[_ schedule-pool server-requests] factory
          state (@server-requests (server-addr this))
          cancelable (.scheduleAtFixedRate
                      ^ScheduledThreadPoolExecutor schedule-pool
                      #(try
                         (ping this)
                         (catch Exception e nil))
                      0 ;; initial delay
                      interval
                      TimeUnit/SECONDS)]
      (swap! (:keep-alive state) assoc this cancelable)))
  (cancel-ping [this]
    (let [[_ _ server-requests] factory
          state (@server-requests (server-addr this))]
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
                     (if-not (nil? handler)
                       (do
                         (deliver (:promise handler) result)
                         (when (:async? handler)
                           (when-let [cb (:callback handler)]
                             (cb result))))
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
                       (.availableProcessors (Runtime/getRuntime)))]
    [(tcp-client-factory handler
                         :codec slacker-base-codec
                         :options *options*
                         :ssl-context ssl-context)
     schedule-pool
     server-requests]))

(defn host-port
  "get host and port from connection string"
  [connection-string]
  (let [[host port] (split connection-string #":")]
    [host (Integer/valueOf ^String port)]))

(defn create-client [slacker-client-factory addr content-type options]
  (let [[host port] (host-port addr)
        [tcp-factory _ server-requests] slacker-client-factory
        client (tcp-client tcp-factory host port :lazy-connect true)
        slacker-client (SlackerClient. addr
                                       client
                                       slacker-client-factory
                                       content-type
                                       options)]
    (swap! server-requests
           (fn [snapshot]
             (if (snapshot addr)
               (update-in snapshot [addr :refs] conj slacker-client)
               (assoc snapshot addr
                      {:pendings (atom {})
                       :idgen (atom 0)
                       :keep-alive (atom {})
                       :refs [slacker-client]}))))
    (when-let [interval (:ping-interval options)]
      (schedule-ping slacker-client interval))
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
  ;; shutdown associated clients
  (doseq [a (map :refs (vals @(nth factory 2)))]
    (doseq [c (flatten a)]
      (close c)))
  (stop-clients (first factory))
  (.shutdown ^ScheduledThreadPoolExecutor (second factory)))
