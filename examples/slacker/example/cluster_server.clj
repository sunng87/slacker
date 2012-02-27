(ns slacker.example.cluster-server
  (:use [slacker server interceptor])
  (:require [slacker.example.api]))

(definterceptor log-function-calls
  :before (fn [req]
            (println (str "calling: " (:fname req)))
            req))

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api)
                        (Integer/valueOf (first args))
                        :cluster {:zk "127.0.0.1:2181"
                                  :name "example-cluster"}
                        :interceptors (interceptors [log-function-calls])))
(println "Slacker example server (cluster enabled) started.")

