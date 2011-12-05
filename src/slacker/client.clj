(ns slacker.client
  (:use [slacker.common])
  (:use [lamina core connections])
  (:use [aleph.tcp])
  (:import [slacker SlackerException]))

(defn- handle-response [code data]
  (cond
   (= code result-code-success) (read-carb (first data))
   (= code result-code-notfound) (throw (SlackerException. "not found"))
   (= code result-code-exception) (throw (SlackerException. (read-carb (first data))))
   :else (throw (SlackerException. (str "invalid result code: " code)))))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this func-name params])
  (async-call-remote [this func-name params cb]))

(deftype SlackerClient [host port conn content-type]
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (let [request [version type-request content-type func-name (write-carb params)]
          response (wait-for-result (conn request) *timeout*)]
      (when-let [[_ _ _ code data] response]
        (handle-response code data))))
  (async-call-remote [this func-name params cb]
    (let [result-promise (promise)]
      (run-pipeline
       (conn [version type-request content-type func-name (write-carb params)])
       #(if-let [[_ _ _ code data] %]
          (let [result (handle-response code data)]
            (deliver result-promise result)
            (if-not (nil? cb) (cb result)))))
      result-promise)))

(defn slackerc
  "Create connection to a slacker server."
  [host port
   & {:keys [content-type]
      :or {content-type content-type-carb}}]
  (let [conn (client #(tcp-client {:host host
                                   :port port
                                   :encoder slacker-request-codec
                                   :decoder slacker-response-codec}))]
    (SlackerClient. host port conn content-type)))

(defn close-slackerc
  "Close the connection"
  [sc]
  (close-connection sc))

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

