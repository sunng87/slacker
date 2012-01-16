(ns slacker.server.cluster
  (:require [zookeeper :as zk])
  (:use [slacker common serialization])
  (:use [clojure.string :only [split]])
  (:import java.net.Socket))

(defn- check-ip
   "check IP address contains?
   if not connect to zookeeper and getLocalAddress"
  [cluster port]
  (if(nil? (cluster :node))
    (let [zk-address (split (cluster :zk) #":")
          zk-ip (first zk-address)
          zk-port (Integer/parseInt (second zk-address))
          socket (Socket. zk-ip zk-port)
          result (.close socket)]
       (assoc cluster :node (str (.getLocalAddress socket) ":" port)))))

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
    (do
      (zk/create zk-conn node-name :persistent? persistent?)
      (if-not (nil? fnmeta)
        (zk/set-data zk-conn node-name (serialize :clj fnmeta :bytes) (:version (zk/exists zk-conn node-name))))))
  )
  
(defn publish-cluster
  "publish server information to zookeeper as cluster for client"
  [cluster port funcs-map]
  (let [zk-conn (zk/connect (cluster :zk))
        cluster-map (check-ip cluster port)
        cluster-name   (str "/" (cluster :name) )
        server-node (cluster :node)
        funcs (keys funcs-map)]
    (do
      (create-node zk-conn cluster-name :persistent? true)
      (create-node zk-conn (str cluster-name   "/servers") :persistent? true)
        (create-node zk-conn (str cluster-name "/functions") :persistent? true)
        (create-node zk-conn (str cluster-name "/functions/" server-node ))
        (doseq [fname funcs]
          (create-node zk-conn (str cluster-name "/functions/" fname  ) :persistent? true :fnmeta (meta (funcs-map fname))))
        (doseq [fname funcs]
          (create-node zk-conn (str cluster-name "/functions/" fname "/" server-node ))
          )
        (if *test* (def *zk-conn* zk-conn ))
    )
    )
)
