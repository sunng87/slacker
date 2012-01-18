(ns slacker.test.client.cluster
  (:use [clojure.test])
  (:use [slacker.client common cluster])
  (:use [slacker.serialization])
  (:use [slacker.utils :only [zk-path]])
  (:require [zookeeper :as zk]))

(deftest test-clustered-client
  (let [cluster-name "test-cluster"
        test-server "127.0.0.1:2104"
        test-server2 "127.0.0.1:2105"
        zk-server "127.0.0.1:2181"
        zk-verify-conn (zk/connect zk-server)
        sc (clustered-slackerc cluster-name zk-server)]
    (zk/create-all zk-verify-conn (zk-path cluster-name "servers" test-server))
    (doseq [f ["hello" "world"]]
      (zk/create-all zk-verify-conn (zk-path cluster-name "functions" f)
                     :persistent? true)
      (zk/set-data zk-verify-conn
                   (zk-path cluster-name "functions" f)
                   (serialize :clj {:name f :doc "test function"} :bytes)
                   (:version (zk/exists
                              zk-verify-conn
                              (zk-path cluster-name "functions" f))))
      (zk/create zk-verify-conn
                 (zk-path cluster-name "functions" f test-server)))

  
    (is (= ["127.0.0.1:2104"] (get-associated-servers sc "hello")))
    (is (= 1 (count (get-all-servers sc))))
    (is (= {:name "world" :doc "test function"}
           (inspect sc :meta "world")))

    (zk/create zk-verify-conn (zk-path cluster-name "servers" test-server2))
    (zk/create zk-verify-conn
               (zk-path cluster-name "functions" "hello" test-server2))

    (Thread/sleep 1000)
    (is (= [test-server test-server2] (@slacker-function-servers "hello")))
    (is (= 2 (count @slacker-clients)))
  
    (zk/delete-all zk-verify-conn (zk-path cluster-name))
    (zk/close zk-verify-conn)

    (close sc)))


