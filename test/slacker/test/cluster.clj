(ns slacker.test.cluster
  (:use [clojure.test] )
  (:use [slacker.server cluster server interceptor])
  (:use [zookeeper :as zk])
  (:require [slacker.test.http]))


(defn- create-data [& args]
  (map str (keys (ns-funcs (the-ns 'slakcer.test.http)))))

(deftest test-publish-cluster
  [& args]
  (let [node-list (create-data)
        cluster-map {:name "test-cluster" :node "127.0.0.1:2104" :zk "127.0.0.1:2181"}
        test-conn (zk/connect (cluster-map :zk))]
		   (do
		   (def *test* true)
		   (start-slacker-server (the-ns 'slacker.test.http) 2104
                           :cluster cluster-map )
		   (is (false? (every? (fn[x](empty? x))(map zk/children (repeat test-conn) node-list))))
		   )
       (zk/close test-conn))
 )

(deftest test-setdown-cluster
  [& args]
  (let [node-list (create-data)
        test-conn (zk/connect "127.0.0.1:2181")
        ]
    (do
      (zk/close *zk-conn*)
      (is (true? (every? (fn[x](false? x))(map zk/children (repeat test-conn) node-list))))
      (zk/close test-conn)
      )
   )
  )
