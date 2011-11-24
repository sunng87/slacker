(ns slacker.client
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp])
  (:import [slacker SlackerException]))

(defn- call-remote [conn func-name params]
  (let [ch (wait-for-result conn)]
    (enqueue ch [version type-request func-name (write-carb params)])
    (if-let [[version type code data] (wait-for-message ch *timeout*)]
      (cond
        (= code result-code-success) (read-carb (first data))
        (= code result-code-notfound) (throw (SlackerException. "not found"))
        (= code result-code-exception) (throw (SlackerException. (read-carb (first data))))
        :else (throw (SlackerException. (str "invalid result code: " code)))))))
  
(defn slackerc [host port]
  (tcp-client {:host host
               :port port
               :encoder slacker-request-codec
               :decoder slacker-response-codec}))

(defn with-slackerc [conn remote-call-info]
  (let [[fname args] remote-call-info]
    (call-remote conn fname args)))

(defmacro defremote [fname]
  `(defn ~fname [& args#]
     [(name '~fname) (into [] args#)]))


