(ns slacker.server
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp]))

(defn- map-req-fields [req]
  (zipmap [:version :packet-type :content-type :fname :data] req))

(defn- check-version [req]
  (if (= version (:version req))
    req
    (assoc req :code result-code-version-mismatch)))

(defn- look-up-function [funcs req]
  (if (nil? (:code req))
    (if-let [func (funcs (:fname req))]
      (assoc req :func func)
      (assoc req :code result-code-notfound))
    req))

(defn- deserialize-params [req]
  (if (nil? (:code req))
    (assoc req :params (read-carb (first (:data req))))
    req))

(defn- do-invoke [req]
  (if (nil? (:code req))
    (try
      (let [{f :func params :params} req
            r (apply f params)]
        (assoc req :result r :code result-code-success))
      (catch Exception e
        (assoc req :code result-code-exception :result (.toString e))))
    req))

(defn- serialize-result [req]
  (if-not (nil? (:result req))
    (assoc req :result (write-carb (:result req)))
    req))

(defn- map-response-fields [req]
  (map (assoc req :type type-response)
       [:version :type :content-type :code :result]))

(defn- create-server-handler [funcs]
  (let [find-func (partial look-up-function funcs)]
    (fn [ch client-info]
      (receive-all ch
                   #(if-let [req %]
                      (enqueue ch (-> req
                                      map-req-fields
                                      check-version
                                      find-func
                                      deserialize-params
                                      do-invoke
                                      serialize-result
                                      map-response-fields)))))))

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


