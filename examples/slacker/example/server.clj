(ns slacker.example.server
  (:use [slacker server interceptor])
  (:use slacker.interceptors.stats)
  (:require [slacker.example.api]))

(definterceptor log-function-calls
  :before (fn [req]
            (println (str "calling: " (:fname req)))
            req))

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api) 2104
                        :interceptors (interceptors [log-function-calls
                                                     function-call-stats]))
  (println "Slacker example server started on port 2104."))

