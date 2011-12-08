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

(def exhausted-actions
  {:fail GenericObjectPool/WHEN_EXHAUSTED_FAIL
   :block GenericObjectPool/WHEN_EXHAUSTED_BLOCK
   :grow GenericObjectPool/WHEN_EXHAUSTED_GROW})

(defn connection-pool
  [host port max-active exhausted-action max-wait max-idle]
  (let [factory (connection-pool-factory host port)]
    (GenericObjectPool. factory
                        max-active
                        (exhausted-actions exhausted-action)
                        max-wait
                        max-idle)))


