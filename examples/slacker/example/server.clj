(ns slacker.example.server
  (:use slacker.server)
  (:require [slacker.example.api]))

(defn log-function-calls [req]
  (println (str "calling: " (:fname req)))
  req)

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api) 2104
                        :before #(-> % log-function-calls))
  (println "Slacker example server started on port 2104."))

