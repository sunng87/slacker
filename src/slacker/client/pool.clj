(ns slacker.client.pool
  (:use [slacker protocol])
  (:use [lamina connections])
  (:use [aleph tcp])
  (:import [org.apache.commons.pool PoolableObjectFactory])
  (:import [org.apache.commons.pool.impl GenericObjectPool]))

(defn- connection-pool-factory [host port]
  (reify
    PoolableObjectFactory
    (destroyObject [this obj]
      (close-connection obj))
    (makeObject [this]
      (client #(tcp-client {:host host
                            :port port
                            :encoder slacker-request-codec
                            :decoder slacker-response-codec})))
    (validateObject [this obj]
      true)
    (activateObject [this obj]
      )
    (passivateObject [this obj]
      )))

(defn connection-pool [host port]
  (let [factory (connection-pool-factory host port)]
    (GenericObjectPool. factory)))


