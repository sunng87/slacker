(ns slacker.client.cluster
  (:require [zookeeper :as zk])
  (:require [slacker.client])
  (:require [slacker.utils :as utils])
  (:use [slacker.client.common])
  (:use [slacker.serialization])
  (:use [clojure.string :only [split]])
  (:require [clojure.tools.logging :as logging])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol CoordinatorAwareClient
  (refresh-associated-servers [this ns])
  (refresh-all-servers [this])
  (get-connected-servers [this])
  (get-ns-mappings [this])
  (delete-ns-mapping [this fname]))

(defmacro defn-remote
  "cluster enabled defn-remote"
  [sc fname & {:keys [remote-ns remote-name async? callback]
               :or {remote-ns (ns-name *ns*)
                    remote-name nil async? false callback nil}}]
  `(do
     (refresh-associated-servers ~sc ~remote-ns)
     (slacker.client/defn-remote
       ~sc ~fname
       :remote-ns ~remote-ns
       :remote-name ~remote-name
       :async? ~async?
       :callback ~callback)))

(defn- create-slackerc [server content-type]
  (let [host (first (split server #":"))
        port (Integer/valueOf (second (split server #":")))]
    (slacker.client/slackerc host port :content-type content-type)))

(defn- find-server [slacker-ns-servers ns-name]
  (if-let [servers (@slacker-ns-servers ns-name)]
    (rand-nth servers)
    (throw+ {:code :not-found})))

(defn- ns-callback [e sc nsname]
  (case (:event-type e)
    :NodeDeleted (delete-ns-mapping sc nsname)
    :NodeChildrenChanged (refresh-associated-servers sc nsname)
    nil))

(defn- clients-callback [e sc]
  (case (:event-type e)
    :NodeChildrenChanged (refresh-all-servers sc) ;;TODO
    nil))

(defn- meta-data-from-zk [zk-conn cluster-name fname]
  (let [fnode (utils/zk-path cluster-name "functions"
                             (utils/escape-zkpath fname))]
    (if-let [node-data (zk/data zk-conn fnode)]
      (deserialize :clj (:data node-data) :bytes))))

(deftype ClusterEnabledSlackerClient
    [cluster-name zk-conn
     slacker-clients slacker-ns-servers content-type]
  CoordinatorAwareClient
  (refresh-associated-servers [this nsname]
    (let [node-path (utils/zk-path cluster-name "namespaces"
                                   (utils/escape-zkpath nsname))
          servers (zk/children zk-conn node-path
                               :watch? true)]
      ;; update servers for this namespace
      (swap! slacker-ns-servers assoc nsname servers)
      ;; establish connection if the server is not connected
      (doseq [s servers]
        (if-not (contains? slacker-clients s)
          (let [sc (create-slackerc s content-type)]
            (logging/info (str "establishing connection to " s))
            (swap! slacker-clients assoc s sc))))
      servers))
  (refresh-all-servers [this]
    (let [node-path (utils/zk-path cluster-name "servers")
          servers (into #{} (zk/children zk-conn node-path :watch? true))]
      ;; close connection to offline servers, remove from slacker-clients
      (doseq [s (keys @slacker-clients)]
        (when-not (contains? servers s)
          (logging/info (str "closing connection of " s))
          (close (@slacker-clients s))
          (swap! slacker-clients dissoc s)))))
  (get-connected-servers [this]
    (keys @slacker-clients))
  (get-ns-mappings [this]
    @slacker-ns-servers)
  (delete-ns-mapping [this ns]
    (swap! slacker-ns-servers dissoc ns))
  
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params]
    (let [fname (str ns-name "/" func-name)
          target-server (find-server slacker-ns-servers ns-name)
          target-conn (@slacker-clients target-server)]
      (logging/debug (str "calling " ns-name "/"
                          func-name " on " target-server))
      (sync-call-remote target-conn ns-name func-name params)))
  (async-call-remote [this ns-name func-name params cb]
    (let [fname (str ns-name "/" func-name)
          target-server (find-server slacker-ns-servers ns-name)
          target-conn (@slacker-clients target-server)]
      (logging/debug (str "calling " ns-name "/"
                          func-name " on " target-server))
      (async-call-remote target-conn ns-name func-name params cb)))
  (close [this]
    (zk/close zk-conn)
    (doseq [s (vals @slacker-clients)] (close s))
    (reset! slacker-clients {})
    (reset! slacker-ns-servers {}))
  (inspect [this cmd args]
    (case cmd
      :functions
      (into []
            (map utils/unescape-zkpath
                 (zk/children zk-conn
                              (utils/zk-path cluster-name "functions"))))
      :meta (meta-data-from-zk zk-conn cluster-name args))))

(defn- on-zk-events [e sc]
  (if (.endsWith (:path e) "servers")
    ;; event on `servers` node
    (clients-callback e sc)
    ;; event on `namespaces` nodes
    (let [matcher (re-matches #"/.+/namespaces/?(.*)" (:path e))]
      (if-not (nil? matcher)
        (ns-callback e sc (utils/unescape-zkpath (second matcher)))))))

(defn clustered-slackerc
  "create a cluster enalbed slacker client"
  [cluster-name zk-server
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [zk-conn (zk/connect zk-server)
        slacker-clients (atom {})
        slacker-ns-servers (atom {})
        sc (ClusterEnabledSlackerClient.
            cluster-name zk-conn
            slacker-clients slacker-ns-servers
            content-type)]
    (zk/register-watcher zk-conn (fn [e] (on-zk-events e sc)))
    ;; watch 'servers' node
    (zk/children zk-conn
                 (utils/zk-path cluster-name "servers") :watch? true)
    sc))


