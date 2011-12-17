(ns slacker.server
  (:use [slacker common serialization protocol])
  (:use [lamina.core])
  (:use [aleph.tcp]))

;; pipeline functions for server request handling
(defn- map-req-fields [req]
  (zipmap [:packet-type :content-type :fname :data] req))

(defn- look-up-function [funcs req]
  (if-let [func (funcs (:fname req))]
    (assoc req :func func)
    (assoc req :code :not-found)))

(defn- deserialize-args [req]
  (if (nil? (:code req))
    (let [data (first (:data req))]
      (assoc req :args
             (deserialize (:content-type req) data)))
    req))

(defn- do-invoke [req]
  (if (nil? (:code req))
    (try
      (let [{f :func args :args} req
            r (apply f args)]
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

(defn build-server-pipeline [funcs before-interceptors after-interceptors]
  (let [find-func (partial look-up-function funcs)
        to-response #(assoc % :packet-type :type-response)]
    #(-> %
         find-func
         deserialize-args
         before-interceptors
         do-invoke
         after-interceptors
         serialize-result
         to-response)))

(defmulti -handle-request (fn [_ p] (:packet-type p)))
(defmethod -handle-request :type-request [server-pipeline req-map]
  (map-response-fields (server-pipeline req-map)))
(defmethod -handle-request :type-ping [_ _]
  pong-packet)
(defmethod -handle-request :default [_ _]
  invalid-type-packet)

(defn handle-request [server-pipeline req client-info]
  (if (= version (first req))
    (let [req-map (assoc (map-req-fields (second req)) :client client-info)]
      (-handle-request server-pipeline req-map))
    protocol-mismatch-packet))

(defn create-server-handler [funcs before after]
  (let [server-pipeline (build-server-pipeline funcs before after)]
    (fn [ch client-info]
      (receive-all
       ch
       #(if-let [req %]
          (enqueue ch (handle-request server-pipeline req client-info)))))))

(defn start-slacker-server
  "Start a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, it's better
  to combine them into one."
  [exposed-ns port
   & {:keys [interceptors]
      :or {interceptors {:before identity :after identity}}}]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        {before :before after :after} interceptors
        handler (create-server-handler funcs before after)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port :frame slacker-base-codec})))


