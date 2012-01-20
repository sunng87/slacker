(ns slacker.test.server.cluster
  (:use [clojure.test] )
  (:use [slacker.server cluster])
  (:use [slacker.utils :only [zk-path]])
  (:use [zookeeper :as zk]))

(def funcs {"plus" + "minus" -})

(defn- create-data [cluster-map]
  (doall
   (map #(zk-path %1 "functions" %2)
        (repeat (cluster-map :name))
        (keys funcs))))

(deftest test-publish-cluster
  (let [cluster-map {:name "test-cluster" :zk "127.0.0.1:2181"}
        node-list (create-data cluster-map)
        test-conn (zk/connect "127.0.0.1:2181")
        zk-conn (zk/connect "127.0.0.1:2181")]
    
    ;; make sure all functions are published 
    (with-zk zk-conn
      (publish-cluster cluster-map 2104 funcs))
    (is (false? (every? (fn[x](false? x))(map zk/children (repeat test-conn) node-list))))
    (is (not (nil? (zk/exists test-conn
                              (zk-path (:name cluster-map)
                                       "servers"
                                       (str "127.0.0.1:2104"))))))
    ;; close the server connection, ephemeral node will be deleted
    (zk/close zk-conn)

    ;; we want to make that the server is no longer listed in our
    ;; serevr directory
    (is (nil? (zk/exists test-conn
                         (zk-path (:name cluster-map)
                                  "servers"
                                  (str "127.0.0.1:2104")))))

    (is (false? (zk/children test-conn (zk-path (:name cluster-map)
                                                "functions"
                                                "plus"
                                                "127.0.0.1:2104"))))
    
    ;; clean up
    (zk/delete-all test-conn (zk-path (:name cluster-map)))
    (zk/close test-conn)))

