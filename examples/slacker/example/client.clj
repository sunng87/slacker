(ns slacker.example.client
  (:use slacker.client)
  (:use [slingshot.slingshot :only [try+]]))

(def conn (slackerc "localhost" 2104))
(def conn2 (slackerc "localhost" 2104 :content-type :json))
(defn-remote conn timestamp :remote-ns "slacker.example.api")
(defn-remote conn2 inc-m :remote-ns "slacker.example.api")
(defn-remote conn get-m :async? true :remote-ns "slacker.example.api")
(defn-remote conn2 show-m
  :remote-name "get-m"
  :remote-ns "slacker.example.api")
(defn-remote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %)
  :remote-ns "slacker.example.api")
(defn-remote conn rand-ints
  :remote-ns "slacker.example.api")
(defn-remote conn make-error
  :remote-ns "slacker.example.api")


(defn -main [& args]
  (println (timestamp))
  (binding [slacker.common/*debug* true]
    (println (inc-m 100)))
  (println (show-m))
  (println @(get-m))
  (get-m2)
  (println (rand-ints 10))
  (try+
    (make-error)
    (catch [:code :exception] {:keys [error]} (println error)))
  ;; shutdown
  (close-slackerc conn)
  (close-slackerc conn2)

  (System/exit 0))
