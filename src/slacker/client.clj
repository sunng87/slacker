(ns slacker.client
  (:use [slacker common serialization protocol])
  (:use [slacker.client pool])
  (:use [lamina.core :exclude [close]])
  (:use [lamina.connections])
  (:use [aleph.tcp])
  (:import [slacker SlackerException]))

(defn- handle-response [content-type code data]
  (case code
   :success ((deserializer content-type) (first data))
   :not-found (throw (SlackerException. "function not found."))
   :exception (throw (SlackerException.
                      ((deserializer content-type) (first data))))
   :protocol-mismatch (throw (SlackerException.
                              "client-server version mismatch."))
   :else (throw (SlackerException. (str "invalid result code: " code)))))

(defn- make-request [content-type func-name params]
  (let [serialized-params ((serializer content-type) params)]
    [version :type-request content-type func-name serialized-params]))

(def ping-packet [version :type-ping 0 nil nil])
(defn ping [conn]
  (wait-for-result (conn ping-packet) *timeout*))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this func-name params])
  (async-call-remote [this func-name params cb])
  (close [this]))

(deftype SlackerClient [conn content-type]
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (let [request (make-request content-type func-name params)
          response (wait-for-result (conn request) *timeout*)]
      (when-let [[_ _ _ code data] response]
        (handle-response content-type code data))))
  (async-call-remote [this func-name params cb]
    (let [request (make-request content-type func-name params)]
      (run-pipeline
       (conn request)
       #(if-let [[_ _ _ code data] %]
          (let [result (handle-response content-type code data)]
            (if-not (nil? cb) (cb result)))))))
  (close [this]
    (close-connection conn)))

(defn slackerc
  "Create connection to a slacker server."
  [host port
   & {:keys [content-type]
      :or {content-type :carb}}]
  (let [conn (client #(tcp-client {:host host
                                   :port port
                                   :encoder slacker-request-codec
                                   :decoder slacker-response-codec}))]
    (SlackerClient. conn content-type)))

(deftype PooledSlackerClient [pool content-type]
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (let [conn (.borrowObject pool)
          request (make-request content-type func-name params)
          response (wait-for-result (conn request) *timeout*)]
      (.returnObject pool conn)
      (when-let [[_ _ _ code data] response]
        (handle-response content-type code data))))
  (async-call-remote [this func-name params cb]
    (let [conn (.borrowObject pool)
          request (make-request content-type func-name params)]
      (run-pipeline
       (conn request)
       #(do
          (.returnObject pool conn)
          (if-let [[_ _ _ code data] %]
            (let [result (handle-response content-type code data)]
              (if-not (nil? cb) (cb result))))))))
  (close [this]
    (.close pool)))

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

(defn with-slackerc
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [sc remote-call-info
   & {:keys [async callback]
      :or {async false callback nil}}]
  (let [[fname args] remote-call-info]
    (if (or async (not (nil? callback)))
      (async-call-remote sc fname args callback)
      (sync-call-remote sc fname args))))

(defmacro defremote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  [sc fname & {:keys [remote-name async callback]
               :or {remote-name nil async false callback nil}}]
  `(defn ~fname [& args#]
     (with-slackerc ~sc
       [(or ~remote-name (name '~fname))
        (into [] args#)]
       :async ~async
       :callback ~callback)))

