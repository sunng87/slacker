(ns slacker.example.client
  (:use slacker.client)
  (:import [slacker SlackerException]))

(def conn (slackerc "localhost" 2104))
(defremote conn timestamp)
(defremote conn inc-m)
(defremote conn get-m :async true)
(defremote conn show-m :remote-name "get-m")
(defremote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %))
(defremote conn rand-ints)
(defremote conn make-error)
(defremote conn first-arg)

(defn -main [& args]
  (println (timestamp))
  (println (inc-m 100))
  (println (show-m))
  (println @(get-m))
  (get-m2)
  (println (rand-ints 10))
  (try
    (make-error)
    (catch SlackerException e (println (.getMessage e)))))
