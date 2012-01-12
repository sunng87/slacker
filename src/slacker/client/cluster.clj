(ns slacker.client.cluster
  (:require [zookeeper :as zk])
  (:require [slacker.client])
  (:use [slacker.client.common])
  (:use [slacker.serialization])
  (:use [clojure.string :only [split]])
  (:use [slingshot.slingshot :only [throw+]]))

(defonce slacker-clients (atom {}))
(defonce slacker-function-servers (atom {}))

(defprotocol CoordinatorAwareClient
  (get-associated-servers [this fname])
  (get-all-servers [this]))

(defmacro defn-remote
  "cluster enabled defn-remote"
  [sc fname & {:keys [remote-name async? callback]
               :or {remote-name nil async? false callback nil?}}]
  `(do
     (get-associated-servers ~sc (or ~remote-name (name '~fname)))
     (slacker.client/defn-remote
       sc fname
       :remote-name remote-name
       :async? async?
       :callback callback)))

(defn- create-slackerc [server content-type]
  (let [host (first (split server #":"))
        port (Integer/valueOf (second (split server #":")))]
    (slacker.client/slackerc host port :content-type content-type)))

(defn- find-sc [func-name]
  (if-let [servers (@slacker-function-servers func-name)]
    (rand-nth servers)
    (throw+ {:code :not-found})))

(defn- functions-callback [sc fname]
  (fn [e]
    (case (:event-type e)
      :NodeDeleted (swap! slacker-function-servers dissoc fname)
      :NodeChildrenChanged (get-associated-servers sc fname)
      nil)))

(defn- clients-callback [sc]
  (fn [e]
    (case (:event-type e)
      :NodeChildrenChanged (get-all-servers sc)
      nil)))

(defn- meta-data-from-zk [zk-conn cluster-name args]
  (let [fnode (str "/" cluster-name "/functions/" args)]
    (if-let [node-data (zk/data zk-conn fnode)]
      (serialize :clj node-data :bytes))))

(deftype ClusterEnabledSlackerClient
    [cluster-name zk-conn content-type]
  CoordinatorAwareClient
  (get-associated-servers [this fname]
    (let [node-path (str "/" cluster-name "/functions/" fname)
          servers (zk/children zk-conn node-path
                               :watch (functions-callback this fname))]
      (if-not (empty? servers)
        (swap! slacker-function-servers
               assoc fname servers)
        (swap! slacker-function-servers
               dissoc fname))
      servers))
  (get-all-servers [this]
    (let [node-path (str "/" cluster-name "/servers" )
          servers (zk/children zk-conn node-path
                               :watch (clients-callback this))]
      (ref-set slacker-clients
                (map #(or (@slacker-clients %)
                          (create-slackerc % content-type)) servers))
      @slacker-clients))
  
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (sync-call-remote (find-sc func-name) func-name params)) 
  (async-call-remote [this func-name params cb]
    (async-call-remote (find-sc func-name) func-name params cb))
  (close [this]
    (zk/close zk-conn)
    (doseq [sc (vals @slacker-clients)]
      (close sc))
    (ref-set slacker-clients {})
    (ref-set slacker-function-servers {}))
  (inspect [this cmd args]
    (case cmd
      :functions (into [] (keys @slacker-function-servers))
      :meta (meta-data-from-zk zk-conn cluster-name args))))

(defn clustered-slackerc
  "create a cluster enalbed slacker client"
  [cluster-name zk-server
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [zk-conn (zk/connect zk-server)]
    (ClusterEnabledSlackerClient. cluster-name zk-conn content-type)))


