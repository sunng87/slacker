(ns slacker.test.cluster
  (:use [clojure.test] )
  (:use [slacker.server cluster])
  (:use [zookeeper :as zk])
  (:require [slacker.test.http]))

(def *test* true)
(def *test-conn* (zk/connect "127.0.0.1:2046"))

(defn- create-data [& args]
  (map str (keys (ns-funcs (the-ns 'slakcer.test.http)))))

(defn- ns-funcs [n]
  (into {}
        (for [[k v] (ns-publics n) :when (fn? @v)] [(name k) v])))

(deftest test-publish-cluster
  [& args]
  (let [node-list (create-data)
        cluster-map {:name "test-cluster" :node "127.0.0.1:2104" :zk "127.0.0.1:2181"}
        test-conn *test-conn*
        ]
		   (do
         (publish-cluster cluster-map 2104 (ns-funcs (the-ns 'slacker.test.http)))
		     (is (false? (every? (fn[x](empty? x))(map zk/children (repeat test-conn) node-list))))
		   )
       )
 )

(deftest test-setdown-cluster
  [& args]
  (let [node-list (create-data)
        test-conn *test-conn*
        ]
    (do
      (zk/close *zk-conn*)
      (is (true? (every? (fn[x](false? x))(map zk/children (repeat test-conn) node-list))))
      (zk/close test-conn)
      )
   )
  )
