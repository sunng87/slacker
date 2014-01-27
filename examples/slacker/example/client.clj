(ns slacker.example.client
  (:use slacker.client)
  (:use [slingshot.slingshot :only [try+]]))

(def conn (slackerc "127.0.0.1:2104"))
(def conn2 (slackerc "127.0.0.1:2104" :content-type :json))
(defn-remote conn slacker.example.api/timestamp)
(defn-remote conn2 slacker.example.api/inc-m)
(defn-remote conn slacker.example.api/get-m :async? true)
(defn-remote conn2 show-m
  :remote-name "get-m"
  :remote-ns "slacker.example.api")
(defn-remote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %)
  :remote-ns "slacker.example.api")
(defn-remote conn slacker.example.api/rand-ints)
(defn-remote conn slacker.example.api/make-error)


(defn -main [& args]
  (println (timestamp))
  (println (inc-m 100))
  (println (show-m))
  (println @(get-m))
  (get-m2)
  (println (rand-ints 10))
  (try+
    (make-error)
    (catch [:code :exception] {:keys [error]} (println error)))

  (close-all-slackerc))
