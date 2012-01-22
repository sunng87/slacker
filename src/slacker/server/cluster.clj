(ns slacker.server.cluster
  (:require [zookeeper :as zk])
  (:use [slacker common serialization])
  (:use [clojure.string :only [split]])
  (:require [slacker.utils :as utils])
  (:import java.net.Socket))

(declare *zk-conn* )

(defn- check-ip
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
   & {:keys [fnmeta persistent?]
      :or {fnmeta nil
           persistent? false}}]
  (if-not (zk/exists zk-conn node-name )
    (zk/create-all zk-conn node-name :persistent? persistent?))
  (if-not (nil? fnmeta)
    (zk/set-data zk-conn node-name
                 (serialize :clj fnmeta :bytes)
                 (:version (zk/exists zk-conn node-name)))))

(defn publish-cluster
  "publish server information to zookeeper as cluster for client"
  [cluster port funcs-map]
  (let [cluster-name (cluster :name)
        server-node (str (or (cluster :node) (check-ip (:zk cluster))) ":" port)
        funcs (map utils/escape-zkpath (keys funcs-map))]
    (create-node *zk-conn* (utils/zk-path cluster-name "servers")
                 :persistent? true)
    (create-node *zk-conn* (utils/zk-path cluster-name "servers" server-node ))
    (doseq [fname funcs]
      (create-node *zk-conn* (utils/zk-path cluster-name "functions" fname  )
                   :persistent? true
                   :fnmeta (select-keys (meta (funcs-map fname))
                                        [:name :doc :arglists])))
    (doseq [fname funcs]
      (create-node *zk-conn*
                   (utils/zk-path cluster-name "functions" fname server-node)))))

(defmacro with-zk
  "publish server information to specifized zookeeper for client"
  [zk-conn & body]
  `(binding [*zk-conn* ~zk-conn]
     ~@body))

