(ns slacker.example.server
  (:use slacker.core)
  (:require [slacker.example.api]))

(defn -main [& args]
  (start-slacker-server (the-ns 'slacker.example.api) 2104))

