(ns slacker.example.cluster-client
  (:use [slacker.common])
  (:use [slacker.client.cluster])
  (:use [slacker.client :only [close-slackerc]])
  (:use [slacker.utils :only [defn-remote-all]]))

(def sc (clustered-slackerc "example-cluster" "127.0.0.1:2181"))

(defn-remote-all 'slacker.example.cluster-client/sc)
(defn-remote sc async-timestamp
  :remote-name "timestamp"
  :async? true)

(defn -main [& args]
  (binding [*debug* true]
    (println (timestamp))
    (println (rand-ints 10))
    (println @(async-timestamp)))

  (dotimes [_ 100] (timestamp))
  
  (close-slackerc sc)
  (System/exit 0))

