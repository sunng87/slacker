(ns slacker.client.cluster
  (:require [zookeeper :as zk])
  (:use [slacker.client :only [slackerc]])
  (:use [slacker.client.common])
  (:use [clojure.string :only [split]])
  (:use [slingshot.slingshot :only [throw+]]))

(defonce slacker-clients (atom {}))
(defonce slacker-function-servers (atom {}))

(defprotocol CoordinatorAwareClient
  (get-associated-servers [fname])
  (get-all-servers []))

(defmacro defn-remote
  "cluster enabled defn-remote"
  [sc fname & {:keys [remote-name async? callback]
               :or {remote-name nil async? false callback nil?}}]
  `(do
     (get-associated-servers ~sc (or ~remote-name (name '~fname)))
     (defn ~fname [& args#]
       (with-slackerc ~sc
         [(or ~remote-name (name '~fname))
          (into [] args#)]
         :async? ~async?
         :callback ~callback))))

(defn- create-slackerc [server content-type]
  (let [host (first (split server #":"))
        port (Integer/valueOf (second (split server #":")))]
    (slackerc host port :content-type content-type)))

(defn- find-sc [func-name]
  (if-let [servers (@slacker-function-servers func-name)]
    (rand-nth servers)
    (throw+ {:code :not-found})))

(deftype ClusterEnabledSlackerClient
    [cluster-name zk-conn content-type]
  CoordinatorAwareClient
  (get-associated-servers [this fname]
    (let [node-path (str "/" cluster-name "/functions/" fname)
          servers (zk/children zk-conn node-path)]
      (if-not (empty? servers)
        (swap! slacker-function-servers
               assoc fname servers)
        (swap! slacker-function-servers
               dissoc fname))
      servers))
  (get-all-servers [this]
    (let [node-path (str "/" cluster-name "/servers" )
          servers (zk/children zk-conn node-path)]
      (doseq [server servers]
        (swap! slacker-clients
               assoc server (create-slackerc server content-type)))))
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (sync-call-remote (find-sc func-name) func-name params)) 
  (async-call-remote [this func-name params cb]
    (async-call-remote (find-sc func-name) func-name params cb))
  (close [this]
    (zk/close zk-conn)
    (doseq [sc (vals @slackerc-clients)]
      (close sc))
    (ref-set! slacker-clients {})
    (ref-set! slacker-function-servers {}))
  (inspect [this cmd args]
    (case cmd
      :functions (into [] (keys @slacker-function-servers))
      :meta (inspect (find-sc args) cmd args))))

(defn clustered-slackerc
  "create a cluster enalbed slacker client"
  [cluster-name zk-server
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [zk-conn (zk/connect zk-server)]
    (ClusterEnabledSlackerClient. cluster-name zk-conn content-type)))


