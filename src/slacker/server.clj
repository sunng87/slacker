(ns slacker.server
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp]))

(defn not-found [& args] "function not found")
(defn create-server-handler [funcs]
  (fn [ch client-info]
    (receive-all ch
                 #(if-let [[version type fname data] %]
                    (do
                      (let [params (read-carb (first data)) ;; gloss buffer
                            f (get funcs fname not-found)
                            r (apply f params)]                        
                        (enqueue ch
                                 [version
                                  type-response
                                  fname
                                  (write-carb r)])))))))

(defn start-slacker-server [exposed-ns port]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        handler (create-server-handler funcs)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port :frame slacker-codec})))


