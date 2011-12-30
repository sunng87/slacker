(ns slacker.example.clientpool
  (:use slacker.client)
  (:import [slacker SlackerException]))

(def conn (slackerc-pool "localhost" 2104
                         :exhausted-action
                         :grow :min-idle 1))

(defn-remote conn timestamp)
(defn-remote conn inc-m)
(defn-remote conn get-m)
(defn-remote conn rand-ints)


(defn -main [& args]
  (println (timestamp))
  (inc-m 300)
  (inc-m 300)
  (println (get-m))
  (println (rand-ints 10))

  ;; shutdown
  (close-slackerc conn)

  (System/exit 0))
