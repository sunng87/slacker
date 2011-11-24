(ns slacker.server
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp]))

(defn- do-invoke [func params]
  (try
    [result-code-success (apply func params)]
    (catch Exception e [result-code-exception (.toString e)])))

(defn- create-server-handler [funcs]
  (fn [ch client-info]
    (receive-all ch
                 #(if-let [[version type fname data] %]
                    (do
                      (let [params (read-carb (first data)) ;; gloss buffer
                            f (get funcs fname)
                            r (if (not (nil? f))                              
                                (do-invoke f params)
                                [result-code-notfound nil])]
                        (enqueue ch
                                 [version
                                  type-response
                                  (first r)
                                  (write-carb (second r))])))))))

(defn start-slacker-server [exposed-ns port]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        handler (create-server-handler funcs)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port
                               :decoder slacker-request-codec
                               :encoder slacker-response-codec})))


