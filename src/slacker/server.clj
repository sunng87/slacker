(ns slacker.server
  (:use [slacker common serialization protocol])
  (:use [slacker.server http])
  (:use [link core tcp http])
  (:require [clojure.tools.logging :as log]
            [slacker.acl.core :as acl]
            [link.ssl :refer [ssl-handler-from-jdk-ssl-context]]
            [link.codec :refer [netty-encoder netty-decoder]])
  (:import [java.util.concurrent TimeUnit
            ThreadPoolExecutor LinkedBlockingQueue RejectedExecutionHandler
            ThreadPoolExecutor$DiscardOldestPolicy ThreadFactory]))

(defn counted-thread-factory
  [name-format daemon]
  (let [counter (atom 0)]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. runnable)
          (.setName name-format)
          (.setDaemon daemon))))))

(defn thread-pool-executor [threads backlog-queue-size]
  (ThreadPoolExecutor. (int threads) (int threads) (long 0)
                       TimeUnit/MILLISECONDS
                       (LinkedBlockingQueue. backlog-queue-size)
                       (counted-thread-factory "slacker-server-worker-%d" true)
                       ^RejectedExecutionHandler (ThreadPoolExecutor$DiscardOldestPolicy.)))

(defmacro with-executor [executor & body]
  `(.submit ~executor
            (cast Runnable (fn [] (try ~@body
                                      (catch Throwable e#
                                        (log/warn e# "Uncaught exception in Slacker executor")))))))

(defn- thread-map-key [client tid]
  (str (:remote-addr client) "::" tid))

;; pipeline functions for server request handling
;; request data structure:
;; [version transaction-id [request-type [content-type func-name params]]]
(defn- map-req-fields [req]
  (assoc (zipmap [:content-type :fname :data]
                 (second (nth req 2)))
    :tid (second req)))

(defn- look-up-function [req funcs]
  (if-let [func (funcs (:fname req))]
    (assoc req :func func)
    (assoc req :code :not-found)))

(defn- deserialize-args [req]
  (if (nil? (:code req))
    (let [data (:data req)]
      (assoc req :args
             (deserialize (:content-type req) data)))
    req))

(defn- do-invoke [req]
  (if (nil? (:code req))
    (try
      (let [{f :func args :args} req
            r0 (apply f args)
            r (if (seq? r0) (doall r0) r0)]
        (assoc req :result r :code :success))
      (catch InterruptedException e
        (log/info "Thread execution interrupted." (:client req) (:tid req))
        (assoc req :code :interrupted))
      (catch Exception e
        (if-not *debug*
          (assoc req :code :exception :result (str e))
          (assoc req :code :exception
                 :result {:msg (.getMessage ^Exception e)
                          :stacktrace (.getStackTrace ^Exception e)}))))
    req))

(defn- serialize-result [req]
  (assoc req :result (serialize (:content-type req) (:result req))))

(defn- map-response-fields [req]
  [version (:tid req) [(:packet-type req)
                       (map req [:content-type :code :result])]])

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

(defn pong-packet [tid]
  [version tid [:type-pong]])
(defn protocol-mismatch-packet [tid]
  [version tid [:type-error [:protocol-mismatch]]])
(defn invalid-type-packet [tid]
  [version tid [:type-error [:invalid-packet]]])
(defn acl-reject-packet [tid]
  [version tid [:type-error [:acl-reject]]])
(defn make-inspect-ack [tid data]
  [version tid [:type-inspect-ack
            [(serialize :clj data :string)]]])

(defn build-server-pipeline [funcs interceptors running-threads]
  #(-> %
       (assoc-current-thread running-threads)
       (look-up-function funcs)
       ((:pre interceptors))
       deserialize-args
       ((:before interceptors))
       do-invoke
       ((:after interceptors))
       serialize-result
       ((:post interceptors))
       (dissoc-current-thread running-threads)
       (assoc :packet-type :type-response)))

;; inspection handler
;; inspect request data structure
;; [version tid  [request-type [cmd data]]]
(defn build-inspect-handler [funcs]
  (fn [req]
    (let [tid (second req)
          [cmd data] (second (nth req 2))
          data (deserialize :clj data :string)]
      (make-inspect-ack
       tid
       (case cmd
         :functions
         (let [nsname (or data "")]
           (filter (fn [x] (.startsWith ^String x nsname)) (keys funcs)))
         :meta
         (let [fname data
               metadata (meta (funcs fname))]
           (select-keys metadata [:name :doc :arglists]))
         nil)))))

(defn interrupt-handler [packet client-info running-threads]
  (let [[target-tid] (second (nth packet 2))
        key (thread-map-key client-info target-tid)]
    (log/debug "About to interrupt" key)
    (when-let [thread (get @running-threads key)]
      (log/debug "Interrupted thread" thread)
      (.interrupt thread)
      (swap! running-threads dissoc key))
    nil))

(defmulti -handle-request (fn [p & _] (first (nth p 2))))
(defmethod -handle-request :type-request [req
                                          server-pipeline
                                          client-info
                                          & _]
  (let [req-map (assoc (map-req-fields req) :client client-info)]
    (map-response-fields (server-pipeline req-map))))
(defmethod -handle-request :type-ping [p & _]
  (pong-packet (second p)))
(defmethod -handle-request :type-inspect-req [p _ _ inspect-handler & _]
  (inspect-handler p))
(defmethod -handle-request :type-interrupt [p _ client-info _ running-threads]
  (interrupt-handler p client-info running-threads))
(defmethod -handle-request :default [p & _]
  (invalid-type-packet (second p)))

(defn handle-request [server-pipeline req client-info inspect-handler acl running-threads]
  (log/debug req)
  (cond
   (not= version (first req))
   (protocol-mismatch-packet 0)

   ;; acl enabled
   (and (not-empty acl)
        (not (acl/authorize client-info acl)))
   (acl-reject-packet (second req))

   ;; handle request

   :else (-handle-request req server-pipeline
                          client-info inspect-handler running-threads)))

(defn- create-server-handler [executor funcs interceptors acl running-threads]
  (let [server-pipeline (build-server-pipeline funcs interceptors running-threads)
        inspect-handler (build-inspect-handler funcs)]
    (create-handler
     (on-message [ch data]
                 (log/debug "data received" data)
                 (with-executor executor
                   (let [client-info {:remote-addr (remote-addr ch)}
                         result (handle-request
                                 server-pipeline
                                 data
                                 client-info
                                 inspect-handler
                                 acl
                                 running-threads)]
                     (log/debug "result" result)
                     (when-not (or (nil? result) (= :interrupted (:code result)))
                       (send! ch result)))))
     (on-error [ch ^Exception e]
               (log/error e "Unexpected error in event loop")
               (close! ch)))))


(defn ns-funcs [n]
  (let [nsname (ns-name n)]
    (into {}
          (for [[k v] (ns-publics n) :when (fn? @v)]
            [(str nsname "/" (name k)) v]))))

(def
  ^{:doc "Default options"}
  server-options
  {:child.so-reuseaddr true,
   :so-reuseaddr true,
   :child.so-keepalive true,
   :tcp-nodelay true,
   :write-buffer-high-water-mark (int 0xFFFF) ; 65kB
   :write-buffer-low-water-mark (int 0xFFF)       ; 4kB
   :child.tcp-nodelay true})

(defn slacker-ring-app
  "Wrap slacker as a ring app that can be deployed to any ring adaptors.
  You can also configure interceptors and acl just like `start-slacker-server`"
  [exposed-ns & {:keys [interceptors acl]
                 :or {acl nil
                      interceptors {:before identity
                                    :after identity
                                    :pre identity
                                    :post identity}}}]
  (let [exposed-ns (if (coll? exposed-ns) exposed-ns [exposed-ns])
        funcs (apply merge (map ns-funcs exposed-ns))
        server-pipeline (build-server-pipeline
                          funcs interceptors nil)]
    (fn [req]
      (let [client-info (http-client-info req)
            curried-handler (fn [req] (handle-request server-pipeline
                                                     req
                                                     client-info
                                                     nil
                                                     acl
                                                     nil))]
        (-> req
            ring-req->slacker-req
            curried-handler
            slacker-resp->ring-resp)))))

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, put
  `exposed-ns` as a vector.
  Options:
  * `interceptors` add server interceptors
  * `http` http port for slacker http transport
  * `acl` the acl rules defined by defrules
  * `ssl-context` the SSLContext object for enabling tls support
  * `executor` custom java.util.concurrent.Executor for tasks execution
  * `threads` size of thread pool if no executor provided
  * `queue-size` size of thread pool task queue if no executor provided"
  [exposed-ns port
   & {:keys [http interceptors acl ssl-context threads queue-size executor]
      :or {http nil
           interceptors {:before identity
                         :after identity
                         :pre identity
                         :post identity}
           acl nil
           ssl-context nil
           threads 10
           queue-size 3000
           executor nil}
      :as options}]
  (let [exposed-ns (if (coll? exposed-ns) exposed-ns [exposed-ns])
        funcs (apply merge (map ns-funcs exposed-ns))
        executor (or executor (thread-pool-executor threads queue-size))
        running-threads (atom {})
        handler (create-server-handler executor funcs interceptors acl running-threads)
        ssl-handler (when ssl-context
                      (ssl-handler-from-jdk-ssl-context ssl-context false))
        handlers [(netty-encoder slacker-base-codec)
                  (netty-decoder slacker-base-codec)
                  handler]
        handlers (if ssl-handler
                   (conj (seq handlers) ssl-handler) handlers)]
    (when *debug* (doseq [f (keys funcs)] (println f)))

    (let [the-tcp-server (tcp-server port handlers
                                         :options server-options)
          the-http-server (when http
                            (http-server http (apply slacker-ring-app exposed-ns
                                                     (flatten (into [] options)))
                                         :threads threads
                                         :ssl-context ssl-context))]
      [the-tcp-server the-http-server])))

(defn stop-slacker-server [server]
  "Takes the value returned by start-slacker-server and stop both tcp and http server if any"
  (doseq [sub-server server]
    (when (not-empty sub-server)
      (stop-server sub-server))))
