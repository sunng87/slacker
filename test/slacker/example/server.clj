(ns slacker.example.server
  (:use slacker.server)
  (:require [slacker.example.api]))

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api) 2104))

