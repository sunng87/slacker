(ns slacker.example.client
  (:require [slacker.client :refer :all]
            [slacker.common :refer [with-extensions]]
            [slacker.protocol :refer [v5]]
            [slacker.serialization.nippy]))

(def conn (slackerc "127.0.0.1:2104" :ping-interval 5 :content-type :nippy))
(def conn2 (slackerc "127.0.0.1:2104" :content-type :transit-json
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
#_(defn-remote conn slacker.example.api/not-found)
(defn-remote conn2 slacker.example.api2/echo2)

(def extension-id 16)

(defn -main [& args]
  #_(not-found 1 2 3)
  (return-nil)
  (println (timestamp))
  (println (inc-m 100))
  (println (show-m))
  (println "Async deferred" @(get-m))
  (with-extensions {extension-id "extension-value"}
    (get-m2))
  (println (rand-ints 10))

  ;; call a function with another client
  (println "Calling timestmap by alternative client: "
           (with-slackerc conn2 (timestamp)))

  ;; call a function by name, without declaring
  (println "Calling timestmap by name: "
           (call-remote conn "slacker.example.api" "timestamp" []))

  ;; call a non-exist function, catched by -slacker-function-not-found
  (println "Calling non-exist function: "
           (call-remote conn "slacker.example.api" "no-such-fn" [1 2 3]))

  (println (echo2 "Echo me."))
  (try
    (make-error)
    (catch Exception e
      (println (ex-data e))))

  (shutdown-slacker-client-factory)
  (shutdown-agents))
