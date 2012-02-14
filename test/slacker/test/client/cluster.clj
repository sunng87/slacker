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
        test-ns "test-ns"
        sc (clustered-slackerc cluster-name zk-server)]
    (zk/create-all zk-verify-conn (zk-path cluster-name "servers" test-server))
    (zk/create-all zk-verify-conn
               (zk-path cluster-name "namespaces" test-ns test-server))
    (doseq [f (map #(str test-ns "/" %) ["hello" "world"])]
      (zk/create-all zk-verify-conn (zk-path cluster-name "functions" f)
                     :persistent? true)
      (zk/set-data zk-verify-conn
                   (zk-path cluster-name "functions" f)
                   (serialize :clj {:name f :doc "test function"} :bytes)
                   (:version (zk/exists
                              zk-verify-conn
                              (zk-path cluster-name "functions" f)))))
  
    (is (= ["127.0.0.1:2104"] (refresh-associated-servers sc test-ns)))

    (is (= {:name (str test-ns "/world") :doc "test function"}
           (inspect sc :meta (str test-ns "/world"))))

    (zk/create zk-verify-conn (zk-path cluster-name "servers" test-server2))
    (zk/create zk-verify-conn
               (zk-path cluster-name "namespaces" test-ns test-server2))

    (Thread/sleep 1000) ;; wait for watchers
    (is (= [test-server test-server2] ((get-ns-mappings sc) test-ns)))
    (is (= 2 (count (get-connected-servers sc))))
    (is (= 2 (count (inspect sc :functions test-ns))))

    (close sc)
    (zk/delete-all zk-verify-conn (zk-path cluster-name))
    (zk/close zk-verify-conn)))


