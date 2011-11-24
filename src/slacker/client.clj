(ns slacker.client
  (:use [slacker.common])
  (:use [lamina.core])
  (:use [aleph.tcp]))

(defn- call-remote [conn func-name params]
  (let [ch (wait-for-result conn)]
    (enqueue ch [version type-request func-name (write-carb params)])
    (if-let [[version type func-name data] (wait-for-message ch *timeout*)]
      (read-carb (first data)))))
  
(defn slacker-client [host port]
  (tcp-client {:host host :port port :frame slacker-codec}))

(defn with-slacker-client [conn remote-call-info]
  (let [[fname args] remote-call-info]
    (call-remote conn fname args)))

(defmacro defremote [fname]
  `(defn ~fname [& args#]
     [(name '~fname) (into [] args#)]))


