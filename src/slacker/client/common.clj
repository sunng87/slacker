(ns ^:no-doc slacker.client.common
  (:require [clojure.tools.logging :as log]
            [clojure.string :refer [split]]
            [link.core :as link :refer :all]
            [link.tcp :refer :all]
            [slacker.protocol :as protocol]
            [link.codec :refer [netty-encoder netty-decoder]]
            [link.ssl :refer [ssl-handler-from-jdk-ssl-context]]
            [rigui.core :as rigui]
            [slacker.serialization :refer [serialize deserialize]]
            [slacker.common :refer :all]
            [slacker.protocol :refer :all]
            [manifold.deferred :as d])
  (:import [java.net ConnectException InetSocketAddress InetAddress]
           [java.nio.channels ClosedChannelException]
           [java.util.concurrent ExecutorService]
           [clojure.lang IFn IDeref IBlockingDeref IPending]
           [io.netty.buffer ByteBuf]))

(defn- handle-valid-response [response]
  (let [[content-type code data extensions] (second response)]
    (case code
      :success {:result data :extensions extensions}
      :not-found {:cause {:error code} :extensions extensions}
      :exception {:cause {:error code :exception data} :extensions extensions}
      :interrupted {:cause {:error code}}
      :thread-pool-full {:cause {:error code}}
      {:cause {:error :invalid-result-code}})))

(defn make-client-hello [client-version client-name]
  [0 [:type-client-hello [client-version client-name]]])

(defn make-request [tid content-type func-name params extensions]
  [tid [:type-request [content-type func-name params extensions]]])

(def ping-packet [0 [:type-ping]])

(defn make-inspect-request [tid cmd args]
  [tid [:type-inspect-req
        [cmd (serialize :clj args)]]])

(defn make-interrupt [target-tid]
  [0 [:type-interrupt [target-tid]]])

(defn parse-inspect-response [response]
  (let [[_ [data]] response]
    (try
      {:result (deserialize :clj data)}
      (finally
        (.release ^ByteBuf data)))))

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
  (close [this])
  (interrupt [this tid]))

(defprotocol KeepAliveClientProtocol
  (schedule-ping [this ping-interval])
  (cancel-ping [this]))

(defprotocol SlackerClientFactoryProtocol
  (schedule-task [this task delay] [this task delay interval])
  (shutdown [this])
  (get-purgatory [this addr])
  (get-purgatory-all [this])
  (open-tcp-client [this host port])
  (assoc-client! [this client])
  (dissoc-client! [this client]))

(defprotocol SlackerClientStateProtocol
  (pending-count [this]))

(deftype DefaultSlackerClientFactory [tcp-factory timer purgatory]
  SlackerClientFactoryProtocol
  (schedule-task [this task delay]
    (rigui/later! timer task delay))
  (schedule-task [this task delay interval]
    (rigui/every! timer task delay interval))
  (shutdown [this]
    ;; shutdown associated clients
    (doseq [a (map :refs (vals @purgatory))]
      (doseq [c (flatten a)]
        (close c)))
    (stop-clients tcp-factory)
    (rigui/stop timer))
  (get-purgatory [this addr]
    (@purgatory addr))
  (get-purgatory-all [this]
    @purgatory)
  (open-tcp-client [this host port]
    (tcp-client tcp-factory host port :lazy-connect true))
  (assoc-client! [this client]
    (swap! purgatory
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
    (swap! purgatory
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

(defn serialize-params [req]
  (assoc req
         :args (serialize (:content-type req) (:data req))
         :extensions (->> (:extensions req)
                          (into [])
                          ;; serialize the second item
                          (mapv #(update % 1 (partial serialize (:content-type req)))))))

(defn deserialize-results [resp]
  (-> resp
      (assoc :result (when-let [data (:result resp)] (deserialize (:content-type resp) data)))
      (assoc :cause
             (when-let [cause (:cause resp)]
               (update-in cause [:exception]
                          #(when % (deserialize (:content-type resp) %)))))
      (assoc :extensions (into {} (map #(update % 1 (partial deserialize (:content-type resp)))
                                       (:extensions resp))))))

(defn- release-buffer! [resp]
  ;; release the buffer
  (when-let [bb (:result resp)]
    (.release ^ByteBuf bb))
  (when-let [exts (not-empty (:extensions resp))]
    (doseq [bb (map second exts)]
      (.release ^ByteBuf bb)))
  (when-let [bb (-> resp :cause :exception)]
    (.release ^ByteBuf bb)))

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
    (let [e (user-friendly-cause call-result)
          user-ex-data (dissoc call-result :result :args :data)]
      (if (instance? Throwable e)
        (throw (ex-info "Slacker client exception" user-ex-data e))
        (throw (ex-info (str "Slacker client error " (:error e)) user-ex-data))))))

(deftype SlackerClient [addr conn ^DefaultSlackerClientFactory factory content-type options]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params call-options]
    (let [call-options (merge options call-options)
          state (get-purgatory factory (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          content-type (:content-type call-options content-type)
          req-data (-> {:fname fname :data params :content-type content-type
                        :extensions (:extensions call-options)}
                       ((:pre (:interceptors call-options) identity))
                       (serialize-params)
                       ((:before (:interceptors call-options) identity)))
          protocol-version (:protocol-version call-options)
          request (protocol/of protocol-version
                               (make-request tid (:content-type req-data)
                                             (:fname req-data) (:args req-data)
                                             (:extensions req-data)))
          backlog (or (:backlog options) *backlog*)
          prms (promise)
          timeout (or (:timeout call-options) *timeout*)

          resp (if-not (> (count @(:pendings state)) backlog 0)
                 (do
                   (swap! (:pendings state) assoc tid {:promise prms})
                   (send! conn request)
                   (try
                     (deref prms timeout nil)
                     (if (realized? prms)
                       @prms
                       (do
                         (swap! (:pendings state) dissoc tid)
                         (when (:interrupt-on-timeout call-options)
                           (interrupt this tid))
                         {:cause {:error :timeout :timeout timeout} :fname fname}))
                     (catch InterruptedException e
                       (log/debug "Client interrupted" tid)
                       (interrupt this tid)
                       (swap! (:pendings state) dissoc tid)
                       {:cause {:error :interrupted} :fname fname})))
                 {:cause {:error :backlog-overflow} :fname fname})]

      (try
        (-> (assoc req-data
                   :cause (:cause resp)
                   :result (:result resp)
                   :extensions (:extensions resp))
            ((:after (:interceptors call-options) identity))
            (deserialize-results)
            ((:post (:interceptors call-options) identity)))
        (finally
          (release-buffer! resp)))))

  (async-call-remote [this ns-name func-name params cb call-options]
    (let [call-options (merge options call-options)
          state (get-purgatory factory (server-addr this))
          fname (str ns-name "/" func-name)
          tid (next-trans-id (:idgen state))
          content-type (:content-type call-options content-type)

          req-data (-> {:fname fname :data params :content-type content-type
                        :extensions (:extensions call-options)}
                       ((:pre (:interceptors call-options) identity))
                       (serialize-params)
                       ((:before (:interceptors call-options) identity)))

          protocol-version (:protocol-version call-options)
          request (protocol/of protocol-version
                               (make-request tid (:content-type req-data)
                                             (:fname req-data) (:args req-data)
                                             (:extensions req-data)))
          backlog (or (:backlog options) *backlog*)
          timeout (or (:timeout call-options) *timeout*)

          post-hook (fn [result]
                      (try
                        (-> (assoc req-data
                                   :cause (:cause result)
                                   :result (:result result)
                                   :extensions (:extensions result))
                            ((:after (:interceptors call-options) identity))
                            (deserialize-results)
                            ((:post (:interceptors call-options) identity)))
                        (finally
                          (release-buffer! result))))

          prms (d/deferred)
          prms_processed (d/chain prms post-hook)
          _ (when cb
              (as-> prms_processed $
                (if-let [executor (:callback-executor call-options)]
                  (d/onto $ executor) $)
                (d/chain $ #(cb (user-friendly-cause %) (:result %)))))
          timeout-check (fn []
                          (when-let [handler (get @(:pendings state) tid)]
                            (swap! (:pendings state) dissoc tid)
                            (let [result {:cause {:error :timeout :timeout timeout} :fname fname}]
                              (d/success! (:promise handler) result))
                            (when (:interrupt-on-timeout call-options)
                              (interrupt this tid))))]
      (if-not (> (count @(:pendings state)) backlog 0)
        (do
          (swap! (:pendings state) assoc
                 tid {:promise prms :async? true})
          (send! conn request)
          (schedule-task factory timeout-check timeout))
        (d/success! prms {:cause {:error :backlog-overflow} :fname fname}))
      prms_processed))
  (inspect [this cmd args]
    (let [state (get-purgatory factory (server-addr this))
          tid (next-trans-id (:idgen state))
          protocol-version (:protocol-version options)
          request (protocol/of protocol-version
                               (make-inspect-request tid cmd args))
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
    (send! conn (protocol/of (:protocol-version options) ping-packet))
    (log/debug "ping"))
  (close [this]
    (cancel-ping this)
    (close! conn)
    (dissoc-client! factory this))
  (server-addr [this]
    addr)
  (interrupt [this tid]
    (log/debug "Sending interrupt." tid)
    (send! conn (protocol/of (:protocol-version options) (make-interrupt tid))))

  KeepAliveClientProtocol
  (schedule-ping [this interval]
    (let [cancelable (schedule-task factory #(ping this) 0 interval)
          state (get-purgatory factory (server-addr this))]
      (swap! (:keep-alive state) assoc this cancelable)))
  (cancel-ping [this]
    (when-let [state (get-purgatory factory (server-addr this))]
      (when-let [cancelable (@(:keep-alive state) this)]
        (rigui/cancel! (.-timer factory) cancelable))))

  SlackerClientStateProtocol
  (pending-count [this]
    (when-let [p (:pendings (get-purgatory factory (server-addr this)))]
      (count @p))))


(defn- create-link-handler
  "The event handler for client"
  [{factory-ref :factory-ref
    client-version :client-version}]
  (create-handler
   (on-message [ch msg]
               (when-let [rmap (->> ch
                                    (channel-hostport)
                                    (get-purgatory @factory-ref)
                                    :pendings)]
                 (let [[_ [tid msg-body]] msg
                       handler (get @rmap tid)]
                   (swap! rmap dissoc tid)
                   (let [result (handle-response msg-body)]
                     (when (not-empty handler)
                       (deliver (:promise handler) result)
                       ;; pong
                       result)))))
   (on-error [ch ^Exception exc]
             (if (or
                  (instance? ConnectException exc)
                  (instance? ClosedChannelException exc))
               (log/warn "Failed to connect to server or connection lost.")
               (log/error exc "Unexpected error in event loop")))
   (on-active [ch]
              (let [addr-str (channel-hostport ch)
                    slacker-client (->> addr-str
                                        (get-purgatory @factory-ref)
                                        :refs
                                        ;; TODO: multiple clients to
                                        ;; same server with different
                                        ;; configuration
                                        (filter #(= addr-str (.-addr %)))
                                        first)
                    slacker-options (.-options slacker-client)
                    protocol-version (:protocol-version slacker-options)
                    client-name (:client-name slacker-options "")]
                (log/debug "slacker-options for this channel:" slacker-options)
                (send! ch
                       (protocol/of protocol-version
                                    (make-client-hello client-version client-name)))))
   (on-inactive [ch]
                (when-let [rmap (->> ch
                                     (channel-hostport)
                                     (get-purgatory @factory-ref)
                                     :pendings)]
                  (doseq [handler (vals @rmap)]
                    (when (not-empty handler)
                      (deliver (:promise handler)
                               {:cause {:error :connection-broken}})))
                  (reset! rmap {})))))

(def ^:dynamic *options*
  {:tcp-nodelay true
   :so-reuseaddr true
   :so-keepalive true
   :write-buffer-high-water-mark (int 0xFFFF) ; 65kB
   :write-buffer-low-water-mark (int 0xFFF) ; 4kB
   :connect-timeout-millis (int 5000)})

(defn create-client-factory [ssl-context]
  (let [purgatory (atom {})
        factory-ref (atom {})
        handler (create-link-handler {:factory-ref factory-ref
                                      :purgatory purgatory
                                      :client-version slacker-version})
        timer (rigui/start 5 10 (fn [f] (f)))
        ssl-handler (when ssl-context
                      (ssl-handler-from-jdk-ssl-context ssl-context true))
        handlers [(netty-encoder protocol/slacker-root-codec)
                  (netty-decoder protocol/slacker-root-codec)
                  handler]
        handlers (if ssl-handler
                   (conj (seq handlers) ssl-handler)
                   handlers)
        factory (DefaultSlackerClientFactory.
                 (tcp-client-factory handlers :options *options*)
                 timer purgatory)]
    ;; pass ref to handler, indirectly
    (reset! factory-ref factory)
    factory))

(defn host-port
  "get host and port from connection string"
  [connection-string]
  (let [[host port] (split connection-string #":")]
    [host (Integer/valueOf ^String port)]))

(defn create-client [slacker-client-factory addr content-type options]
  (let [[host port] (host-port addr)
        host (.. (InetSocketAddress. ^String host ^int port) (getAddress) (getHostAddress))
        client-ch (open-tcp-client slacker-client-factory host port)
        slacker-client (SlackerClient. (str host ":" port)
                                       client-ch
                                       slacker-client-factory
                                       content-type
                                       options)]
    (assoc-client! slacker-client-factory slacker-client)
    (when-let [interval (:ping-interval options)]
      (schedule-ping slacker-client (* 1000 interval)))
    slacker-client))

(def ^:dynamic *sc* nil)
(def ^:dynamic *callback* nil)

(defn invoke-slacker
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [sc remote-call-info
   & {:keys [async? callback]
      :or {async? false callback nil}
      :as options}]
  (let [sc @(or *sc* sc)  ;; allow local binding to override client
        [nsname fname args] remote-call-info
        ;; merge static extensions and invoke-scope extensions
        options (update options :extensions merge *extensions*)]
    (if (or *callback* async? callback)
      ;; async
      (d/chain
       (async-call-remote sc nsname fname args (or *callback* callback) options)
       process-call-result)

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

(defn clients-remote
  "get clients information on remote server"
  [sc]
  (let [call-result (inspect @sc :clients nil)]
    (if (nil? (:cause call-result))
      (:result call-result)
      (throw (ex-info "Slacker client error"
                      (user-friendly-cause call-result))))))

(defn shutdown-factory [factory]
  (shutdown factory))
