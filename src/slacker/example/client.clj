(ns slacker.example.client
  (:use slacker.core))

(defremote timestamp)
(defremote inc-m)
(defremote get-m)

(defn -main [& args]
  (with-slacker-client (slacker-client "localhost" 2104)
    (inc-m 200)
    (inc-m 100)
    (get-m)))
