(ns slacker.client
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp])
  (:import [slacker SlackerException]))

(defn- handle-response [code data]
  (cond
   (= code result-code-success) (read-carb (first data))
   (= code result-code-notfound) (throw (SlackerException. "not found"))
   (= code result-code-exception) (throw (SlackerException. (read-carb (first data))))
   :else (throw (SlackerException. (str "invalid result code: " code)))))

(defn- sync-call-remote [conn func-name params]
  (let [ch (wait-for-result conn)]
    (enqueue ch [version type-request func-name (write-carb params)])
    (if-let [[version type code data] (wait-for-message ch)]
      (handle-response code data))))

(defn- async-call-remote [conn func-name params cb]
  (let [result (run-pipeline
                conn
                (fn [ch]
                  (enqueue ch [version type-request func-name (write-carb params)])
                  (read-channel ch *timeout*)))]
    (on-success
     result
     #(when-let [[_ _ code data] %]
        (when-not (nil? cb)
          (cb (handle-response code data)))))
    (on-error
     result
     #(throw (SlackerException. %)))))

(defn slackerc [host port]
  (tcp-client {:host host
               :port port
               :encoder slacker-request-codec
               :decoder slacker-response-codec}))

(defn with-slackerc
  [conn remote-call-info
   & {:keys [async callback]
      :or {async false callback nil}}]
  (let [[fname args] remote-call-info]
    (if async
      (async-call-remote conn fname args callback)
      (sync-call-remote conn fname args))))

(defmacro defremote
  [sc fname]
  `(defn ~fname [& args#]
     (with-slackerc ~sc [(name '~fname) (into [] args#)])))

