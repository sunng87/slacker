(ns slacker.client
  (:use [slacker common protocol])
  (:use [slacker.client common pool])
  (:use [lamina.connections])
  (:use [aleph.tcp])
  (:use [gloss.io :only [contiguous]])
  (:use [clojure.string :only [split]])
  (:import [slacker.client.common SlackerClient])
  (:import [slacker.client.pool PooledSlackerClient]))

(defn slackerc
  "Create connection to a slacker server."
  [addr
   & {:keys [content-type]
      :or {content-type :carb}
      :as _}]
  (let [[host port] (host-port addr)
        conn (client #(tcp-client {:host host
                                   :port port
                                   :frame slacker-base-codec}))]
    (SlackerClient. conn content-type)))


(defn slackerc-pool
  "Create a auto resizable connection pool to slacker server.
  You can set arguments for the pool to control the number of
  connection and the policy when pool is exhausted. Check commons
  pool javadoc for the meaning of each argument:
  http://commons.apache.org/pool/apidocs/org/apache/commons/pool/impl/GenericObjectPool.html"
  [connection-string
   & {:keys [content-type max-active exhausted-action max-wait max-idle]
      :or {content-type :carb
           max-active 8
           exhausted-action :block
           max-wait -1
           max-idle 8}
      :as _}]
  (let [[host port] (host-port connection-string)
        pool (connection-pool host port
                              max-active exhausted-action max-wait max-idle)]
    (PooledSlackerClient. pool content-type)))

(defn close-slackerc [client]
  (close client))


(defmacro defn-remote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  ([sc fname & {:keys [remote-ns remote-name async? callback]
                :or {remote-ns (ns-name *ns*)
                     remote-name nil async? false callback nil}}]
     `(let [rname# (or ~remote-name (name '~fname))]
        (def ~fname
          (with-meta
            (fn [& args#]
              (with-slackerc ~sc
                [~remote-ns rname# (into [] args#)]
                :async? ~async?
                :callback ~callback))
            (merge (meta-remote ~sc (str ~remote-ns "/" rname#))
                   {:slacker-remote-fn true
                    :slacker-client ~sc
                    :slacker-remote-ns ~remote-ns
                    :slacker-remote-name rname#}))))))

(defn- defn-remote*
  [sc-sym fname]
  (eval (list 'defn-remote sc-sym
              (symbol (second (split fname #"/")))
              :remote-ns (first (split fname #"/")))))

(defn use-remote
  "import remote functions the current namespace"
  [sc-sym rns]
  (dorun (map defn-remote*
              (repeat sc-sym)
              (inspect @(resolve sc-sym) :functions (name rns)))))

