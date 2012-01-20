(ns slacker.client.cluster
  (:require [zookeeper :as zk])
  (:require [slacker.client])
  (:require [slacker.utils :as utils])
  (:use [slacker.common])
  (:use [slacker.client.common])
  (:use [slacker.serialization])
  (:use [clojure.string :only [split]])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- make-slacker-clients-state []
  (let [slacker-clients (atom {})]
    (add-watch slacker-clients :auto-close
               (fn [_ _ old-value new-value]
                 (doseq [server-addr (keys old-value)]
                   (if-not (contains? new-value server-addr)
                     (close (get old-value server-addr))))))
    slacker-clients))

(defprotocol CoordinatorAwareClient
  (refresh-associated-servers [this fname])
  (refresh-all-servers [this])
  (get-connected-servers [this])
  (get-function-mappings [this])
  (delete-function-mapping [this fname]))

(defmacro defn-remote
  "cluster enabled defn-remote"
  [sc fname & {:keys [remote-name async? callback]
               :or {remote-name nil async? false callback nil}}]
  `(do
     (refresh-associated-servers ~sc (or ~remote-name (name '~fname)))
     (slacker.client/defn-remote
       ~sc ~fname
       :remote-name ~remote-name
       :async? ~async?
       :callback ~callback)))

(defn- create-slackerc [server content-type]
  (let [host (first (split server #":"))
        port (Integer/valueOf (second (split server #":")))]
    (slacker.client/slackerc host port :content-type content-type)))

(defn- find-server [slacker-function-servers func-name]
  (if-let [servers (@slacker-function-servers func-name)]
    (rand-nth servers)
    (throw+ {:code :not-found})))

(defn- functions-callback [e sc fname]
  (case (:event-type e)
    :NodeDeleted (delete-function-mapping sc fname)
    :NodeChildrenChanged (refresh-associated-servers sc fname)
    nil))

(defn- clients-callback [e sc]
  (case (:event-type e)
    :NodeChildrenChanged (refresh-all-servers sc)
    nil))

(defn- meta-data-from-zk [zk-conn cluster-name args]
  (let [fnode (utils/zk-path cluster-name "functions" args)]
    (if-let [node-data (zk/data zk-conn fnode)]
      (deserialize :clj (:data node-data) :bytes))))

(deftype ClusterEnabledSlackerClient
    [cluster-name zk-conn slacker-clients slacker-function-servers content-type]
  CoordinatorAwareClient
  (refresh-associated-servers [this fname]
    (let [node-path (utils/zk-path cluster-name "functions" fname)
          servers (zk/children zk-conn node-path
                               :watch? true)]
      (if-not (empty? servers)
        (swap! slacker-function-servers
               assoc fname servers)
        (swap! slacker-function-servers
               dissoc fname))
      servers))
  (refresh-all-servers [this]
    (let [node-path (utils/zk-path cluster-name "servers" )
          servers (zk/children zk-conn node-path
                               :watch? true)]
      (if servers
        (reset! slacker-clients
                (into {} (map
                          #(vector % (or (get @slacker-clients %)
                                         (create-slackerc % content-type)))
                          servers))))
      @slacker-clients))
  (get-connected-servers [this]
    (keys @slacker-clients))
  (get-function-mappings [this]
    @slacker-function-servers)
  (delete-function-mapping [this fname]
    (swap! slacker-function-servers dissoc fname))
  
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (let [target-server (find-server slacker-function-servers func-name)
          target-conn (@slacker-clients target-server)]
      (if *debug*
        (println (str "[dbg] calling "
                      func-name " on " target-server)))
      (sync-call-remote target-conn func-name params)))
  (async-call-remote [this func-name params cb]
    (let [target-server (find-server slacker-function-servers func-name)
          target-conn (@slacker-clients target-server)]
      (if *debug*
        (println (str "[dbg] calling "
                      func-name " on " target-server)))
      (async-call-remote target-conn func-name params cb)))
  (close [this]
    (zk/close zk-conn)
    (reset! slacker-clients {})
    (reset! slacker-function-servers {}))
  (inspect [this cmd args]
    (case cmd
      :functions (into [] (keys @slacker-function-servers))
      :meta (meta-data-from-zk zk-conn cluster-name args))))

(defn- on-zk-events [e sc]
  (if (.endsWith (:path e) "servers")
    (clients-callback e sc)
    (let [matcher (re-matches #"/.+?/functions/?(.*)" (:path e))]
      (if-not (nil? matcher)
        (functions-callback e sc (second matcher))))))

(defn clustered-slackerc
  "create a cluster enalbed slacker client"
  [cluster-name zk-server
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [zk-conn (zk/connect zk-server)
        slacker-clients (make-slacker-clients-state)
        slacker-function-servers (atom {})
        sc (ClusterEnabledSlackerClient.
            cluster-name zk-conn
            slacker-clients slacker-function-servers
            content-type)]
    (zk/register-watcher zk-conn (fn [e] (on-zk-events e sc)))
    (refresh-all-servers sc)
    sc))


