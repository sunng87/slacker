(ns slacker.test.cluster
  (:use [clojure.test] )
  (:use [slacker.server cluster])
  (:use [zookeeper :as zk])
  (:require [slacker.example.api]))

(def *test-conn* (zk/connect "127.0.0.1:2181"))
(def *server-conn* (zk/connect "127.0.0.1:2181" ))


(defn- ns-funcs [n]
  (into {}
        (for [[k v] (ns-publics n) :when (fn? @v)] [(name k) v])))

(defn- create-data [cluster-map]
  (do
    (map str (repeat(str "/" (cluster-map :name) "/functions/")) (keys (ns-funcs (the-ns 'slacker.example.api))))))

(deftest test-publish-cluster
  (let [cluster-map {:name "test-cluster" :node "127.0.0.1:2104" :zk "127.0.0.1:2181"}
        node-list (create-data cluster-map)
        test-conn *test-conn*
        zk-conn *server-conn*
        ]
    (do
      (with-zk cluster-map 2104 (ns-funcs (the-ns 'slacker.example.api)) zk-conn)
      (is (false? (every? (fn[x](empty? x))(map zk/children (repeat test-conn) node-list))))
      )
    ))

(deftest test-setdown-cluster
  (let [cluster-map {:name "test-cluster" :node "127.0.0.1:2104" :zk "127.0.0.1:2181"}
        node-list (create-data cluster-map)
        test-conn *test-conn*
        zk-conn *server-conn*
        ]
    (do
      (zk/close zk-conn)
      (is (true? (every? (fn[x](nil? x))(map zk/children (repeat test-conn) node-list))))
      (zk/close test-conn)
      )
   )
 )
