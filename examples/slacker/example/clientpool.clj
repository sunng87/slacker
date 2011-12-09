(ns slacker.example.clientpool
  (:use slacker.client)
  (:import [slacker SlackerException]))

(def conn (slackerc-pool "localhost" 2104
                         :exhausted-action
                         :grow :min-idle 1))

(defremote conn timestamp)
(defremote conn inc-m)
(defremote conn get-m)
(defremote conn rand-ints)


(defn -main [& args]
  (println (timestamp))
  (inc-m 300)
  (inc-m 300)
  (println (get-m))
  (println (rand-ints 10))

  ;; shutdown
  (close conn)

  (System/exit 0))
