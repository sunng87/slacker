(ns slacker.test.client.cluster
  (:use [clojure.test])
  (:use [slacker.client.cluster])
  (:use [slacker.serialization])
  (:require [zookeeper :as zk]))

(def cluster-name "test-cluster")
(def test-server "127.0.0.1:2104")
(def zk-server "127.0.0.1:2181")

(def zk-conn (zk/connect zk-server))

(deftest test-clustered-client
  (zk/create-all zk-conn (str "/" cluster-name "/servers/" test-server))
  (doseq [f ["hello" "world"]]
    (zk/create-all zk-conn (str "/" cluster-name "/functions/" f)
                   :persistent? true)
    (zk/set-data zk-conn
                 (str "/" cluster-name "/functions/" f)
                 (serialize :clj {:name f :doc "test function"} :bytes)
                 (:version (zk/exists
                            zk-conn
                            (str "/" cluster-name "/functions/" f))))
    (zk/create zk-conn
               (str "/" cluster-name "/functions/" f "/" test-server)))

  (let [sc (clustered-slackerc cluster-name zk-server)]
    (is (= ["127.0.0.1:2104"] (get-associated-servers sc "hello")))
    (is (= 1 (count (get-all-servers sc)))))
  
  (zk/delete-all zk-conn (str "/" cluster-name))
  (zk/close zk-conn))


