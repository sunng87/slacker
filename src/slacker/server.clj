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
             ((deserializer (:content-type req)) data)))
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
    (assoc req :result ((serializer (:content-type req)) (:result req)))
    req))

(defn- map-response-fields [req]
  [version (map req [:packet-type :content-type :code :result])])

(def pong-packet [version [:type-pong]])
(def protocol-mismatch-packet [version [:type-error :protocol-mismatch]])
(def invalid-type-packet [version [:type-error :invalid-packet]])

(defmacro interceptors [& interceptors]
  `#(-> % ~@interceptors))

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

(defn handle-request [server-pipeline req client-info]
  (if (= version (first req))
    (let [req-map (assoc (map-req-fields (second req)) :client client-info)]
      (case (:packet-type req-map)
        :type-request (map-response-fields (server-pipeline req-map))
        :type-ping pong-packet
        invalid-type-packet))
    protocol-mismatch-packet))

(defn create-server-handler [funcs before after]
  (let [server-pipeline (build-server-pipeline funcs before after)]
    (fn [ch client-info]
      (receive-all
       ch
       #(if-let [req %]
          (enqueue ch (handle-request server-pipeline req client-info)))))))

(defn start-slacker-server
  "Starting a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, it's better
  to combine them into one."
  [exposed-ns port
   & {:keys [before after]
      :or {before identity after identity}}]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        handler (create-server-handler funcs before after)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port :frame slacker-base-codec})))


