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

(defn- sync-call-remote [conn func-name params]
  (let [request [version type-request func-name (write-carb params)]
        response (wait-for-result (conn request))]
    (when-let [[_ _ code data] response]
      (handle-response code data))))

(defn- async-call-remote [conn func-name params cb]
  (let [result-promise (promise)]
    (run-pipeline
     (conn [version type-request func-name (write-carb params)])
     #(if-let [[_ _ code data] %]
        (let [result (handle-response code data)]
          (deliver result-promise result)
          (if-not (nil? cb)
            (cb result)))))
    result-promise))

(defn slackerc
  "Create connection to a slacker server."
  [host port]
  (client #(tcp-client {:host host
                                  :port port
                                  :encoder slacker-request-codec
                                  :decoder slacker-response-codec})))

(defn close-slackerc
  "Close the connection"
  [sc]
  (close-connection sc))

(defn with-slackerc
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [conn remote-call-info
   & {:keys [async callback]
      :or {async false callback nil}}]
  (let [[fname args] remote-call-info]
    (if (or async (not (nil? callback)))
      (async-call-remote conn fname args callback)
      (sync-call-remote conn fname args))))

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

