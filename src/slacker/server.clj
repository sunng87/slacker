(ns slacker.server
  (:use [slacker common serialization protocol])
  (:use [slacker.server http])
  (:use [link core tcp http threads])
  (:require [clojure.tools.logging :as log]
            [slacker.acl.core :as acl])
  (:import [java.util.concurrent Executors]))

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

(defn build-server-pipeline [funcs interceptors]
  #(-> %
       (look-up-function funcs)
       ((:pre interceptors))
       deserialize-args
       ((:before interceptors))
       do-invoke
       ((:after interceptors))
       serialize-result
       ((:post interceptors))
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

(defmulti -handle-request (fn [p & _] (first (nth p 2))))
(defmethod -handle-request :type-request [req
                                          server-pipeline
                                          client-info
                                          _]
  (let [req-map (assoc (map-req-fields req) :client client-info)]
    (map-response-fields (server-pipeline req-map))))
(defmethod -handle-request :type-ping [p & _]
  (pong-packet (second p)))
(defmethod -handle-request :type-inspect-req [p _ _ inspect-handler]
  (inspect-handler p))
(defmethod -handle-request :default [p & _]
  (invalid-type-packet (second p)))

(defn handle-request [server-pipeline req client-info inspect-handler acl]
  (cond
   (not= version (first req))
   (protocol-mismatch-packet 0)

   ;; acl enabled
   (and (not-empty acl)
        (not (acl/authorize client-info acl)))
   (acl-reject-packet (second req))

   ;; handle request

   :else (-handle-request req server-pipeline
                          client-info inspect-handler)))

(defn- create-server-handler [funcs interceptors acl]
  (let [server-pipeline (build-server-pipeline funcs interceptors)
        inspect-handler (build-inspect-handler funcs)]
    (create-handler
     (on-message [ch data]
                 (let [client-info {:remote-addr (remote-addr ch)}
                       result (handle-request
                               server-pipeline
                               data
                               client-info
                               inspect-handler
                               acl)]
                   (send! ch result)))
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
                 :or {acl nil}}]
  (let [exposed-ns (if (coll? exposed-ns) exposed-ns [exposed-ns])
        funcs (apply merge (map ns-funcs exposed-ns))
        server-pipeline (build-server-pipeline
                          funcs interceptors)]
    (fn [req]
      (let [client-info (http-client-info req)
            curried-handler (fn [req] (handle-request server-pipeline
                                                     req
                                                     client-info
                                                     nil
                                                     acl))]
        (-> req
            ring-req->slacker-req
            curried-handler
            slacker-resp->ring-resp)))))

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, put
  `exposed-ns` as a vector.
  Options:
  * interceptors add server interceptors
  * http http port for slacker http transport
  * acl the acl rules defined by defrules
  * ssl-context the SSLContext object for enabling tls support"
  [exposed-ns port
   & {:keys [http interceptors acl ssl-context threads]
      :or {http nil
           interceptors {:before identity
                         :after identity
                         :pre identity
                         :post identity}
           acl nil
           ssl-context nil
           threads 10}
      :as options}]
  (let [exposed-ns (if (coll? exposed-ns) exposed-ns [exposed-ns])
        funcs (apply merge (map ns-funcs exposed-ns))
        executor (new-executor threads)
        handler (create-server-handler funcs interceptors acl)
        handler-spec {:handler handler
                      :executor executor}]
    (when *debug* (doseq [f (keys funcs)] (println f)))

    (let [the-tcp-server (tcp-server port handler-spec
                                         :codec slacker-base-codec
                                         :options server-options
                                         :ssl-context ssl-context)
          the-http-server (when http
                            (http-server http (apply slacker-ring-app exposed-ns
                                                     (flatten (into [] options)))
                                         :executor executor
                                         :ssl-context ssl-context))]
      [the-tcp-server the-http-server])))

(defn stop-slacker-server [server]
  "Takes the value returned by start-slacker-server and stop both tcp and http server if any"
  (doseq [sub-server server]
    (when (not-empty sub-server)
      (stop-server sub-server))))
