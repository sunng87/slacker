(ns slacker.client
  (:use [slacker common protocol])
  (:use [slacker.client common pool])
  (:use [lamina.connections])
  (:use [aleph.tcp])
  (:use [gloss.io :only [contiguous]])
  (:import [slacker.client.common SlackerClient])
  (:import [slacker.client.pool PooledSlackerClient]))

(defn slackerc
  "Create connection to a slacker server."
  [host port
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [conn (client #(tcp-client {:host host
                                   :port port
                                   :frame slacker-base-codec}))]
    (SlackerClient. conn content-type)))


(defn slackerc-pool
  "Create a auto resizable connection pool to slacker server.
  You can set arguments for the pool to control the number of
  connection and the policy when pool is exhausted. Check commons
  pool javadoc for the meaning of each argument:
  http://commons.apache.org/pool/apidocs/org/apache/commons/pool/impl/GenericObjectPool.html"
  [host port
   & {:keys [content-type max-active exhausted-action max-wait max-idle]
      :or {content-type :carb
           max-active 8
           exhausted-action :block
           max-wait -1
           max-idle 8}}]
  (let [pool (connection-pool host port
                              max-active exhausted-action max-wait max-idle)]
    (PooledSlackerClient. pool content-type)))

(defn close-slackerc [client]
  (close client))


(defmacro defn-remote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  [sc fname & {:keys [remote-name async? callback]
               :or {remote-name nil async? false callback nil}}]
  `(defn ~fname [& args#]
     (with-slackerc ~sc
       [(or ~remote-name (name '~fname))
        (into [] args#)]
       :async? ~async?
       :callback ~callback)))

