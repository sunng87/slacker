(ns slacker.server
  (:refer-clojure :exclude [send])
  (:use [slacker common serialization protocol])
  (:use [slacker.server http cluster])
  (:use [slacker.acl.core])
  (:use [link core tcp http])
  (:use [slingshot.slingshot :only [try+]])
  (:require [zookeeper :as zk])
  (:require [clojure.tools.logging :as log])
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
    (try+
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
  (if-not (nil? (:result req))
    (assoc req :result (serialize (:content-type req) (:result req)))
    req))

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
       deserialize-args
       ((:before interceptors))
       do-invoke
       ((:after interceptors))
       serialize-result
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
   (and (not (nil? acl))
        (not (authorize client-info acl)))
   (acl-reject-packet (second req))
   
   ;; handle request

   :else (-handle-request req server-pipeline
                          client-info inspect-handler)))

(defn- create-server-handler [funcs interceptors acl debug]
  (let [server-pipeline (build-server-pipeline funcs interceptors)
        inspect-handler (build-inspect-handler funcs)]
    (create-handler
     (on-message [ch data addr]
                 (binding [*debug* debug]
                   (let [client-info {:remote-addr addr}
                         result (handle-request
                                 server-pipeline
                                 data
                                 client-info
                                 inspect-handler
                                 acl)]
                     (send ch result))))
     (on-error [^Exception e]
               (log/error e "Unexpected error in event loop")))))


(defn- ns-funcs [n]
  (let [nsname (ns-name n)]
    (into {}
          (for [[k v] (ns-publics n) :when (fn? @v)]
            [(str nsname "/" (name k)) v]))))

(def
  ^{:doc "Default TCP options"}
  tcp-options
  {"child.reuseAddress" true,
   "reuseAddress" true,
   "child.keepAlive" true,
   "child.connectTimeoutMillis" 100,
   "tcpNoDelay" true,
   "readWriteFair" true,
   "child.tcpNoDelay" true})

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, it's better
  to combine them into one.
  Options:
  * interceptors add server interceptors
  * http http port for slacker http transport
  * cluster publish server information to zookeeper
  * acl the acl rules defined by defrules"
  [exposed-ns port
   & {:keys [http interceptors cluster acl]
      :or {http nil
           interceptors {:before identity :after identity}
           cluster nil
           acl nil}}]
  (let [exposed-ns (if (coll? exposed-ns) exposed-ns [exposed-ns])
        funcs (apply merge (map ns-funcs exposed-ns))
        handler (create-server-handler funcs interceptors acl *debug*)
        worker-pool (Executors/newCachedThreadPool)]
    
    (when *debug* (doseq [f (keys funcs)] (println f)))
    
    (tcp-server port handler 
                :codec slacker-base-codec
                :worker-pool worker-pool
                :tcp-options tcp-options)
    (when-not (nil? http)
      (http-server http (wrap-http-server-handler
                         (build-server-pipeline funcs interceptors))
                   :worker-pool worker-pool
                   :debug *debug*))
    (when-not (nil? cluster)
      (with-zk (zk/connect (:zk cluster))
        (publish-cluster cluster port
                         (map ns-name exposed-ns) funcs)))))


