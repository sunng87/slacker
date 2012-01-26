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
  (refresh-associated-servers [this ns])
  (refresh-all-servers [this])
  (get-connected-servers [this])
  (get-function-mappings [this])
  (delete-function-mapping [this fname]))

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

(defn- find-server [slacker-function-servers func-name]
  (if-let [servers (@slacker-function-servers func-name)]
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
      (when-not (empty? servers)
        (swap! slacker-ns-servers assoc nsname servers)
        ;; establish connection if the server is not connected
        (doseq [s servers]
          (if-not (contains? slacker-clients s)
            (let [sc (create-slackerc s content-type)]
              (swap! slacker-clients assoc s sc)))))
      servers))
  (refresh-all-servers [this]
    (let [node-path (utils/zk-path cluster-name "servers" )]
      (zk/children zk-conn node-path :watch? true)))
  (get-connected-servers [this]
    (keys @slacker-clients))
  (get-function-mappings [this]
    @slacker-ns-servers)
  (delete-ns-mapping [this ns]
    (swap! slacker-ns-servers dissoc ns))
  
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
      :functions
      (into []
            (map utils/unescape-zkpath
                 (zk/children zk-conn
                              (utils/zk-path cluster-name "functions"))))
      :meta (meta-data-from-zk zk-conn cluster-name args))))

(defn- on-zk-events [e sc]
  (if (.endsWith (:path e) "servers")
    (clients-callback e sc)
    (let [matcher (re-matches #"/.+/namespaces/?(.*)" (:path e))]
      (if-not (nil? matcher)
        (ns-callback e sc (utils/unescape-zkpath (second matcher)))))))

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


