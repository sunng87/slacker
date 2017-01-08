(ns slacker.example.client
  (:require [slacker.client :refer :all]
            [slacker.protocol :refer [v5]]
            [slacker.serialization.nippy]))

(def conn (slackerc "127.0.0.1:2104" :ping-interval 5 :content-type :nippy))
(def conn2 (slackerc "127.0.0.1:2104" :content-type :json
                     :protocol-version v5))
(defn-remote conn slacker.example.api/timestamp)
(defn-remote conn2 slacker.example.api/inc-m)
(defn-remote conn slacker.example.api/get-m :async? true)
(defn-remote conn2 show-m
  :remote-name "get-m"
  :remote-ns "slacker.example.api")
(defn-remote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %2)
  :remote-ns "slacker.example.api")
(defn-remote conn slacker.example.api/rand-ints)
(defn-remote conn slacker.example.api/make-error)
(defn-remote conn slacker.example.api/return-nil)
#(defn-remote conn slacker.example.api/not-found)

(defn -main [& args]
  #_(not-found 1 2 3)
  (return-nil)
  (println (timestamp))
  (println (inc-m 100))
  (println (show-m))
  (println @(get-m))
  (get-m2)
  (println (rand-ints 10))

  ;; call a function with another client
  (println (with-slackerc conn2 (timestamp)))

  (try
    (make-error)
    (catch Exception e
      (println (ex-data e))))

  (shutdown-slacker-client-factory)
  (shutdown-agents))
