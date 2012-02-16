(ns slacker.server.cluster
  (:require [zookeeper :as zk])
  (:use [slacker common serialization])
  (:use [clojure.string :only [split]])
  (:require [slacker.utils :as utils])
  (:import java.net.Socket))

(declare *zk-conn* )

(defn- auto-detect-ip
  "check IP address contains?
   if not connect to zookeeper and getLocalAddress"
  [zk-addr]
  (let [zk-address (split zk-addr #":")
        zk-ip (first zk-address)
        zk-port (Integer/parseInt (second zk-address))
        socket (Socket. zk-ip zk-port)
        local-ip (.getHostAddress (.getLocalAddress socket))]
    (.close socket)
    local-ip))

(defn- create-node
  "get zk connector & node  :persistent?
   check whether exist already
   if not ,create & set node data with func metadata
   "
  [zk-conn node-name
   & {:keys [data persistent?]
      :or {data nil
           persistent? false}}]
  (if-not (zk/exists zk-conn node-name )
    (zk/create-all zk-conn node-name :persistent? persistent?))
  (if-not (nil? data)
    (zk/set-data zk-conn node-name data
                 (:version (zk/exists zk-conn node-name)))))

(defn publish-cluster
  "publish server information to zookeeper as cluster for client"
  [cluster port ns-names funcs-map]
  (let [cluster-name (cluster :name)
        server-node (str (or (cluster :node)
                             (auto-detect-ip (:zk cluster)))
                         ":" port)
        funcs (keys funcs-map)]
    (create-node *zk-conn* (utils/zk-path cluster-name "servers")
                 :persistent? true)
    (create-node *zk-conn*
                 (utils/zk-path cluster-name "servers" server-node ))
    (doseq [nn ns-names]
      (create-node *zk-conn* (utils/zk-path cluster-name "namespaces" nn)
                   :persistent? true)
      (create-node *zk-conn* (utils/zk-path cluster-name "namespaces"
                                            nn server-node)))
    (doseq [fname funcs]
      (create-node *zk-conn*
                   (utils/zk-path cluster-name "functions" fname  )
                   :persistent? true
                   :data (serialize
                          :clj
                          (select-keys
                           (meta (funcs-map fname))
                           [:name :doc :arglists])
                          :bytes)))))

(defmacro with-zk
  "publish server information to specifized zookeeper for client"
  [zk-conn & body]
  `(binding [*zk-conn* ~zk-conn]
     ~@body))

