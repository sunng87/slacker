(ns slacker.example.client
  (:use slacker.client)
  (:import [slacker SlackerException]))

(defremote timestamp)
(defremote inc-m)
(defremote get-m)
(defremote rand-ints)
(defremote make-error)
(defremote first-arg)

(def conn (slackerc "localhost" 2104))

(defn -main [& args]
  (println (with-slackerc conn (timestamp)))
  (println (with-slackerc conn (inc-m 100)))
  (println (with-slackerc conn (inc-m 200) :async true))
  (println (with-slackerc conn (get-m)))
  (println (with-slackerc conn (rand-ints 10)))
  (try
    (with-slackerc conn (make-error))
    (catch SlackerException e (println (.getMessage e)))))
