(ns slacker.example.server
  (:use [slacker server interceptor])
  (:use slacker.interceptors.stats)
  (:use slacker.interceptors.exectime)
  (:use slacker.interceptors.logargs)
  (:require [slacker.example.api]))

(definterceptor log-function-calls
  :before (fn [req]
            (println (str "calling: " (:fname req)))
            req))

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api) 2104
                        :interceptors (interceptors [
                                                     function-call-stats])
                        :http 4104)
  (println "Slacker example server started on port 2104, http enabled on 4104"))

