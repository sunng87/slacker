(ns slacker.example.client
  (:use slacker.client)
  (:import [slacker SlackerException]))

(def conn (slackerc "localhost" 2104))
(def conn2 (slackerc "localhost" 2104 :content-type :json))
(defremote conn timestamp)
(defremote conn2 inc-m)
(defremote conn get-m :async true)
(defremote conn2 show-m :remote-name "get-m")
(defremote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %))
(defremote conn rand-ints)
(defremote conn make-error)


(defn -main [& args]
  (println (timestamp))
  (binding [slacker.common/*debug* true]
    (println (inc-m 100)))
  (println (show-m))
  (println @(get-m))
  (get-m2)
  (println (rand-ints 10))
  (try
    (make-error)
    (catch SlackerException e (println (.getMessage e))))
  ;; shutdown
  (close conn)
  (close conn2)

  (System/exit 0))
