(ns slacker.server
  (:use [slacker common serialization protocol])
  (:use [lamina.core])
  (:use [aleph.tcp]))

;; pipeline functions for server request handling
(defn- map-req-fields [req]
  (transient (zipmap [:version :packet-type :content-type :fname :data] req)))

(defn- look-up-function [funcs req]
  (if (nil? (:code req))
    (if-let [func (funcs (:fname req))]
      (assoc! req :func func)
      (assoc! req :code :not-found))
    req))

(defn- deserialize-params [req]
  (if (nil? (:code req))
    (let [data (first (:data req))]
      (assoc! req :params
             ((deserializer (:content-type req)) data)))
    req))

(defn- do-invoke [req]
  (if (nil? (:code req))
    (try
      (let [{f :func params :params} req
            r (apply f params)]
        (assoc! req :result r :code :success))
      (catch Exception e
        (assoc! req :code :exception :result (.toString e))))
    req))

(defn- serialize-result [req]
  (if-not (nil? (:result req))
    (assoc! req :result ((serializer (:content-type req)) (:result req)))
    req))

(defn- map-response-fields [req]
  (map (persistent! req)
       [:version :packet-type :content-type :code :result]))

(defn build-server-pipeline [funcs]
  (let [find-func (partial look-up-function funcs)
        to-response #(assoc! % :packet-type :type-response)]
    #(-> %
         find-func
         deserialize-params
         do-invoke
         serialize-result
         to-response)))

(defn- create-server-handler [funcs]
  (let [server-pipeline (build-server-pipeline funcs)]
    (fn [ch client-info]
      (receive-all
       ch
       #(if-let [req %]
          (enqueue ch
                   (map-response-fields
                    (let [req-map (map-req-fields req)]
                      (if (= version (:version req-map))
                        (case (:packet-type req-map)
                          :type-request (server-pipeline req-map)
                          (assoc! req-map
                                  :code :invalid-packet
                                  :packet-type :type-error))
                        (assoc! req-map
                                :code :protocol-mismatch
                                :packet-type :type-error))))))))))

(defn start-slacker-server
  "Starting a slacker server to expose all public functions under
  a namespace. If you have multiple namespace to expose, it's better
  to combine them into one."
  [exposed-ns port]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        handler (create-server-handler funcs)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port
                               :decoder slacker-request-codec
                               :encoder slacker-response-codec})))


