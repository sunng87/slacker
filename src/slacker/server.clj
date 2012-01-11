(ns slacker.server
  (:use [slacker common serialization protocol])
  (:use [slacker.server http])
  (:use [lamina.core])
  (:use [aleph tcp http])
  (:use [gloss.io :only [contiguous]])
  (:use [slingshot.slingshot :only [try+]]))

;; pipeline functions for server request handling
(defn- map-req-fields [req]
  (zipmap [:packet-type :content-type :fname :data] req))

(defn- look-up-function [req funcs]
  (if-let [func (funcs (:fname req))]
    (assoc req :func func)
    (assoc req :code :not-found)))

(defn- deserialize-args [req]
  (if (nil? (:code req))
    (let [data (contiguous (:data req))]
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
        (assoc req :code :exception :result (.toString e))))
    req))

(defn- serialize-result [req]
  (if-not (nil? (:result req))
    (assoc req :result (serialize (:content-type req) (:result req)))
    req))

(defn- map-response-fields [req]
  [version (map req [:packet-type :content-type :code :result])])

(def pong-packet [version [:type-pong]])
(def protocol-mismatch-packet [version [:type-error :protocol-mismatch]])
(def invalid-type-packet [version [:type-error :invalid-packet]])
(defn make-inspect-ack [data]
  [version [:type-inspect-ack
            (serialize :clj data :string)]])

(defn build-server-pipeline [funcs interceptors]
  #(-> %
       (look-up-function funcs)
       deserialize-args
       ((:before interceptors))
       do-invoke
       ((:after interceptors))
       serialize-result
       (assoc :packet-type :type-response)))

(defn build-inspect-handler [inspect-enabled? funcs]
  #(if inspect-enabled?
     (case (second %)
       :functions
       (make-inspect-ack (map name (keys funcs)))
       :meta
       (make-inspect-ack
        (let [fname (deserialize :clj (last %) :string)
              metadata (meta (funcs fname))]
          (select-keys metadata [:name :doc :arglists]))))
     (make-inspect-ack nil)))

(defmulti -handle-request (fn [_ p & _] (first p)))
(defmethod -handle-request :type-request [server-pipeline req client-info _]
  (let [req-map (assoc (map-req-fields req) :client client-info)]
    (map-response-fields (server-pipeline req-map))))
(defmethod -handle-request :type-ping [& _]
  pong-packet)
(defmethod -handle-request :type-inspect-req [_ p _ inspect-handler]
  (inspect-handler p))
(defmethod -handle-request :default [& _]
  invalid-type-packet)

(defn handle-request [server-pipeline req client-info inspect-handler]
  (if (= version (first req))
    (-handle-request server-pipeline (second req)
                     client-info inspect-handler)
    protocol-mismatch-packet))

(defn- create-server-handler [funcs interceptors inspect?]
  (let [server-pipeline (build-server-pipeline funcs interceptors)
        inspect-handler (build-inspect-handler inspect? funcs)]
    (fn [ch client-info]
      (receive-all
       ch
       #(if-let [req %]
          (enqueue ch (handle-request server-pipeline
                                      req
                                      client-info
                                      inspect-handler)))))))

(defn- ns-funcs [n]
  (into {}
        (for [[k v] (ns-publics n) :when (fn? @v)] [(name k) v])))

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, it's better
  to combine them into one.
  Options:
  * interceptors add server interceptors
  * http http port for slacker http transport
  * inspect? enable inspect interface, default true"
  [exposed-ns port
   & {:keys [http interceptors inspect?]
      :or {http nil
           interceptors {:before identity :after identity}
           inspect? true}}]
  (let [funcs (ns-funcs exposed-ns)
        handler (create-server-handler funcs interceptors inspect?)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port :frame slacker-base-codec})
    (when-not (nil? http)
      (start-http-server (wrap-ring-handler
                          (wrap-http-server-handler
                           (build-server-pipeline funcs interceptors)))
                         {:port http}))))


