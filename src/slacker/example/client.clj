(ns slacker.example.client
  (:use slacker.client))

(defremote timestamp)
(defremote inc-m)
(defremote get-m)
(defremote rand-ints)

(def conn (slackerc "localhost" 2104))

(defn -main [& args]
  (println (with-slackerc conn (timestamp)))
  (println (with-slackerc conn (inc-m 100)))
  (println (with-slackerc conn (inc-m 200)))
  (println (with-slackerc conn (get-m)))
  (println (with-slackerc conn (rand-ints 10))))
