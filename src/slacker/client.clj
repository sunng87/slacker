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
  (run-pipeline conn
    (fn [ch]
      (enqueue ch [version type-request func-name (write-carb params)])
      (if-let [[version type code data] (read-channel ch *timeout*)]
        (cb (handle-response code data))))))
  
(defn slackerc [host port]
  (tcp-client {:host host
               :port port
               :encoder slacker-request-codec
               :decoder slacker-response-codec}))

(defn with-slackerc
  [conn remote-call-info
   & {:keys [async]
      :or {async false}}]
  (let [[fname args callback] remote-call-info]
    (if async
      (async-call-remote conn fname args callback)
      (sync-call-remote conn fname args))))

(defmacro defremote
  [fname & {:keys [callback] :or {callback nil}}]
  `(defn ~fname [& args#]
       [(name '~fname) (into [] args#) ~callback]))

