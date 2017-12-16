(ns slacker.server
  (:require [clojure.tools.logging :as log]
            [link.core :as link :refer :all]
            [link.tcp :refer :all]
            [link.http :refer :all]
            [slacker.common :refer :all]
            [slacker.serialization :refer [serialize deserialize]]
            [slacker.protocol :as protocol]
            [slacker.server.http :refer :all]
            [slacker.interceptor :as interceptor]
            [link.ssl :refer [ssl-handler-from-jdk-ssl-context]]
            [link.codec :refer [netty-encoder netty-decoder]])
  (:import [java.util.concurrent TimeUnit ExecutorService
            ThreadPoolExecutor LinkedBlockingQueue RejectedExecutionHandler
            RejectedExecutionException ThreadPoolExecutor$AbortPolicy ThreadFactory]
           [io.netty.buffer ByteBuf]))

(defn- counted-thread-factory
  [name-format daemon]
  (let [counter (atom 0)]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. runnable)
          (.setName (format name-format (swap! counter inc)))
          (.setDaemon daemon))))))

(defn- thread-pool-executor [threads backlog-queue-size]
  (ThreadPoolExecutor. (int threads) (int threads) (long 0)
                       TimeUnit/MILLISECONDS
                       (LinkedBlockingQueue. ^long backlog-queue-size)
                       (counted-thread-factory "slacker-server-worker-%d" true)
                       ^RejectedExecutionHandler (ThreadPoolExecutor$AbortPolicy.)))

(defmacro ^:private with-executor [executor & body]
  `(.submit ~executor
            ^Runnable (cast Runnable
                            (fn [] (try ~@body
                                       (catch Throwable e#
                                         (log/warn e# "Uncaught exception in Slacker executor")))))))

(defn- thread-map-key [client tid]
  (str (:remote-addr client) "::" tid))

;; pipeline functions for server request handling
;; request data structure:
;; [version transaction-id [request-type [content-type func-name params]]]
(defn- map-req-fields [req]
  (let [[prot-ver [tid [_ data]]] req]
    (assoc (zipmap [:content-type :fname :data :raw-extensions] data)
           :tid tid
           :protocol-version prot-ver)))

(defn- look-up-function [req funcs]
  (if-let [func (funcs (:fname req))]
    (let [[ns-name fn-name] (clojure.string/split (:fname req) #"/" 2)]
      (assoc req :func func :ns-name ns-name :fn-name fn-name))
    (assoc req :code :not-found)))

(defn- deserialize-args [req]
  (if (nil? (:code req))
    (let [data (:data req)
          content-type (:content-type req)
          extensions (:raw-extensions req)]
      (assoc req
             :args (deserialize content-type data)
             :extensions (into {} (mapv #(update % 1 (partial deserialize content-type))
                                        extensions))))
    req))

(defn- do-invoke [req]
  (if (nil? (:code req))
    (try
      (with-extensions (:extensions req)
        (let [{f :func args :args} req
              r0 (apply f args)
              r (if (seq? r0) (doall r0) r0)]
          (assoc req :result r :code :success)))
      (catch InterruptedException e
        (log/info "Thread execution interrupted." (:client req) (:tid req))
        (assoc req :code :interrupted))
      (catch Throwable e
        (if-not *debug*
          (assoc req :code :exception :result (str e) :exception e)
          (assoc req :code :exception
                 :result {:msg (.getMessage ^Exception e)
                          :stacktrace (.getStackTrace ^Exception e)}))))
    req))

(defn- call-interceptor [req interceptor]
  (try
    (interceptor req)
    (catch Throwable e
      (if-not *debug*
        (assoc req :code :exception :result (str e) :exception e)
        (assoc req :code :exception
               :result {:msg (.getMessage ^Exception e)
                        :stacktrace (.getStackTrace ^Exception e)})))))

(defn- serialize-result [req]
  (assoc req
         :result (serialize (:content-type req) (:result req))
         :raw-extensions (->> (:extensions req)
                          (into [])
                          (mapv #(update % 1 (partial serialize (:content-type req)))))))

(defn- map-response-fields [req]
  (protocol/of (:protocol-version req)
               [(:tid req) [:type-response
                            (mapv req [:content-type :code :result :raw-extensions])]]))

(defn- assoc-current-thread [req running-threads]
  (if running-threads
    (let [client (:client req)
          key (thread-map-key client (:tid req))]
      (log/debug "thread-map-key" key)
      (swap! running-threads assoc key (Thread/currentThread))
      (assoc req :thread-map-key key))
    req))

(defn- dissoc-current-thread [req running-threads]
  (when running-threads
    (let [key (:thread-map-key req)]
      (log/debug "thread-map-key" key)
      (swap! running-threads dissoc key)))
  req)

(defmacro ^:private def-packet-fn [name args & content]
  `(defn- ~name [prot-ver# tid# ~@args]
     (protocol/of prot-ver# [tid# [~@content]])))

(def-packet-fn pong-packet []
  :type-pong)
#_(def-packet-fn protocol-mismatch-packet []
  :type-error [:protocol-mismatch])
(def-packet-fn error-packet [code]
  :type-error [code])
(def-packet-fn make-inspect-ack [data]
  :type-inspect-ack [data])

(defn ^:no-doc build-server-pipeline [interceptors running-threads]
  #(-> %
       (assoc-current-thread running-threads)
       (call-interceptor (:pre interceptors identity))
       deserialize-args
       (call-interceptor (:before interceptors identity))
       do-invoke
       (call-interceptor (:after interceptors identity))
       serialize-result
       (call-interceptor (:post interceptors identity))
       (dissoc-current-thread running-threads)))

;; inspection handler
;; inspect request data structure
;; [version tid  [request-type [cmd data]]]
(defn ^:no-doc build-inspect-handler [funcs]
  (fn [req]
    (let [[prot-ver [tid [_ [cmd byte-block]]]] req]
      (try
        (let [data (deserialize :clj byte-block)
              results (case cmd
                        :functions
                        (let [nsname (or data "")]
                          (filter #(clojure.string/starts-with? % nsname) (keys funcs)))
                        :meta
                        (let [fname data
                              metadata (meta (funcs fname))]
                          (select-keys metadata [:name :doc :arglists]))
                        nil)
              sresult (serialize :clj results)]
          (make-inspect-ack prot-ver tid sresult))
        (finally
          (.release ^ByteBuf byte-block))))))

(defn- interrupt-handler [packet client-info running-threads]
  (let [[_ [_ [_ [target-tid]]]] packet
        key (thread-map-key client-info target-tid)]
    (log/debug "About to interrupt" key)
    (when-let [thread (get @running-threads key)]
      (log/debug "Interrupted thread" thread)
      (.interrupt ^Thread thread)
      (swap! running-threads dissoc key))
    nil))

(defn- release-buffer! [req-map]
  ;; release the buffer
  (when-let [byte-block (:data req-map)]
    (.release ^ByteBuf byte-block))
  (when-let [exts (not-empty (:raw-extensions req-map))]
    (doseq [bb (map second exts)]
      (.release ^ByteBuf bb))))

;; p: [version [tid [type ...]]]
(defmulti ^:private -handle-request (fn [p & _] (let [[_ [_ [packet-type]]] p] packet-type)))
(defmethod -handle-request :type-request [req
                                          server-pipeline
                                          client-info
                                          inspect-handler
                                          running-threads
                                          executors
                                          funcs]
  (let [req-map (assoc (map-req-fields req) :client client-info)
        req-map (look-up-function req-map funcs)]
    (if (nil? (:code req-map))
      (if-let [thread-pool (get executors (:ns-name req-map)
                                          (:default executors))]
        ;; async run on dedicated or global thread pool
        ;; http call always runs on default thread
        (do
          (try
            (with-executor ^ThreadPoolExecutor thread-pool
              (try
                (let [result (map-response-fields (server-pipeline req-map))]
                  (when-not (or (nil? result) (= :interrupted (:code result)))
                    (link/send! (:channel client-info) result)))
                (finally
                  (release-buffer! req-map))))
            ;; async run, return nil so sync wait won't send
            nil
            (catch RejectedExecutionException _
              (log/warn "Server thread pool is full for" (:ns-name req-map))
              (release-buffer! req-map)
              (error-packet (:version req-map) (:tid req-map) :thread-pool-full))))
        ;; run on default thread
        (try
          (map-response-fields (server-pipeline req-map))
          (finally
            (release-buffer! req-map))))
      ;; return error
      (try
        (error-packet (:version req-map) (:tid req-map) :not-found)
        (finally
          (release-buffer! req-map))))))
(defmethod -handle-request :type-ping [[version [tid _]] & _]
  (pong-packet version tid))
(defmethod -handle-request :type-inspect-req [p _ _ inspect-handler & _]
  (inspect-handler p))
(defmethod -handle-request :type-interrupt [p _ client-info _ running-threads & _]
  (interrupt-handler p client-info running-threads))
(defmethod -handle-request :default [[version [tid _]] & _]
  (error-packet version tid :invalid-packet))

(defn ^:no-doc handle-request
  [server-pipeline req client-info inspect-handler running-threads executors funcs]
  (log/debug req)
  (-handle-request req server-pipeline
                   client-info inspect-handler running-threads executors funcs))

(defn- create-server-handler [executors funcs interceptors running-threads]
  (let [server-pipeline (build-server-pipeline interceptors running-threads)
        inspect-handler (build-inspect-handler funcs)]
    (create-handler
     (on-message [ch data]
                 (log/debug "data received" data)
                 (let [client-info {:remote-addr (remote-addr ch)
                                    :channel ch}
                       result (handle-request
                               server-pipeline
                               data
                               client-info
                               inspect-handler
                               running-threads
                               executors
                               funcs)]
                   (log/debug "result" result)
                   (when-not (or (nil? result) (= :interrupted (:code result)))
                     (send! ch result))))
     (on-error [ch ^Exception e]
               (log/error e "Unexpected error in event loop")
               (close! ch)))))


(defn ^:no-doc parse-funcs [n]
  (if (map? n)
    ;; expose via map
    (into {}
          (mapcat #(let [[nsname fns] %]
                     (for [[fnname thefn] fns]
                       [(str nsname "/" fnname) thefn]))
                  n))

    ;; expose via namespace
    (let [nsname (ns-name n)]
      (into {}
            (for [[k v] (ns-publics n)
                  :when (let [md (meta v)]
                          (and (not (:macro md))
                               (not (:no-slacker md))
                               (fn? @v)))]
              [(str nsname "/" (name k)) v])))))

(def ^:no-doc server-options
  {:child.so-reuseaddr true,
   :so-reuseaddr true,
   :child.so-keepalive true,
   :child.tcp-nodelay true})

(defn slacker-ring-app
  "Wrap slacker as a ring app that can be deployed to any ring adaptors.
  You can also configure interceptors just like `start-slacker-server`"
  [fn-coll & {:keys [interceptors]
              :or {interceptors interceptor/default-interceptors}}]
  (let [fn-coll (if (vector? fn-coll) fn-coll [fn-coll])
        funcs (apply merge (map parse-funcs fn-coll))
        server-pipeline (build-server-pipeline interceptors nil)]
    (fn [req]
      (let [client-info (http-client-info req)
            curried-handler (fn [req] (handle-request server-pipeline
                                                     req
                                                     client-info
                                                     nil
                                                     nil
                                                     nil
                                                     nil))]
        (-> req
            ring-req->slacker-req
            curried-handler
            slacker-resp->ring-resp)))))

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace, or a map or functions. If you have multiple namespace or map
  to expose, put `fn-coll` as a vector.

  `fn-coll` examples:

  * `(the-ns 'slacker.example.api)`: expose all public functions under
    `slacker.example.api`, except those marked with `^:no-slacker`
  * `{\"slacker.example.api2\" {\"echo2 \" (fn [a] a) ...}}` expose all functions
    in this map
  * `[(the-ns 'slacker.example.api) {...}]` a vector of normal function collection

  Options:

  * `interceptors` add server interceptors
  * `http` http port for slacker http transport
  * `ssl-context` the SSLContext object for enabling tls support
  * `executor` custom java.util.concurrent.ExecutorService for tasks execution, note this executor will be shutdown when you stop the slacker server
  * `threads` size of thread pool if no executor provided
  * `queue-size` size of thread pool task queue if no executor provided
  * `executors` a map of dedicated executors, key by namespace. You can configure dedicated thread pool for certain namespace."
  [fn-coll port
   & {:keys [http interceptors ssl-context threads queue-size executor executors]
      :or {interceptors interceptor/default-interceptors
           threads 10
           queue-size 3000
           executor nil
           executors {}}
      :as options}]
  (let [fn-coll (if (vector? fn-coll) fn-coll [fn-coll])
        funcs (apply merge (map parse-funcs fn-coll))
        default-executor (or executor (thread-pool-executor threads queue-size))
        executors (assoc executors :default default-executor)
        running-threads (atom {})
        handler (create-server-handler executors funcs interceptors running-threads)
        ssl-handler (when ssl-context
                      (ssl-handler-from-jdk-ssl-context ssl-context false))
        handlers [(netty-encoder protocol/slacker-root-codec)
                  (netty-decoder protocol/slacker-root-codec)
                  handler]
        handlers (if ssl-handler
                   (conj (seq handlers) ssl-handler) handlers)]
    (when *debug* (doseq [f (keys funcs)] (println f)))

    (let [the-tcp-server (tcp-server port handlers
                                         :options server-options)
          the-http-server (when http
                            (http-server http (apply slacker-ring-app fn-coll
                                                     (flatten (into [] options)))
                                         :threads threads
                                         :ssl-context ssl-context))]
      [the-tcp-server the-http-server executors])))

(defn stop-slacker-server [server]
  "Takes the value returned by start-slacker-server and stop both tcp and http server if any"
  (let [[the-tcp-server the-http-server executors] server]
    (dorun (pmap #(do
                    (.shutdown ^ThreadPoolExecutor %)
                    (.awaitTermination ^ThreadPoolExecutor % *timeout* TimeUnit/MILLISECONDS))
                 (vals executors)))

    (when (not-empty the-tcp-server)
      (stop-server the-tcp-server))
    (when (not-empty the-http-server)
      (stop-server the-http-server))))
