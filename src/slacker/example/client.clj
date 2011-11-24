(ns slacker.example.client
  (:use slacker.core))

(defremote timestamp)
(defremote inc-m)
(defremote get-m)

(def conn (slacker-client "localhost" 2104))

(defn -main [& args]
  (println (with-slacker-client conn (timestamp)))
  (println (with-slacker-client conn (inc-m 100)))
  (println (with-slacker-client conn (inc-m 200)))
  (println (with-slacker-client conn (get-m))))
