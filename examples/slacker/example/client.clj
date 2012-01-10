(ns slacker.example.client
  (:use slacker.client)
  (:use [slingshot.slingshot :only [try+]]))

(def conn (slackerc "localhost" 2104))
(def conn2 (slackerc "localhost" 2104 :content-type :json))
(defn-remote conn timestamp)
(defn-remote conn2 inc-m)
(defn-remote conn get-m :async? true)
(defn-remote conn2 show-m :remote-name "get-m")
(defn-remote conn get-m2
  :remote-name "get-m"
  :callback #(prn "Async get-m ==> " %))
(defn-remote conn rand-ints)
(defn-remote conn make-error)


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