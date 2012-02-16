(ns slacker.client.common
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common])
  (:use [lamina.core :exclude [close]])
  (:use [lamina.connections])
  (:use [gloss.io :only [contiguous]])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- handle-normal-response [response]
  (let [[_ content-type code data] response]
    (case code
      :success (deserialize content-type (contiguous data))
      :not-found (throw+ {:code code})
      :exception (let [einfo (deserialize content-type (contiguous data))]
                   (if-not (map? einfo)
                     (throw+ {:code code :error einfo})
                     (let [e (Exception. (:msg einfo))]
                       (.setStackTrace e (:stacktrace einfo))
                       (throw+ e))))
      (throw+ {:code :invalid-result-code}))))

(defn handle-response [response]
  (case (first response)
    :type-response (handle-normal-response response)
    :type-error (throw+ {:code (second response)})
    nil))

(defn make-request [content-type func-name params]
  (let [serialized-params (serialize content-type params)]
    [version [:type-request content-type func-name serialized-params]]))

(def ping-packet [version :type-ping 0 nil nil])
(defn ping [conn]
  (wait-for-result (conn ping-packet) *timeout*))

(defn make-inspect-request [cmd args]
  [version [:type-inspect-req cmd
            (serialize :clj args :string)]])
(defn parse-inspect-response [response]
  (deserialize :clj (second (second response)) :string))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params])
  (async-call-remote [this ns-name func-name params cb])
  (inspect [this cmd args])
  (close [this]))

(deftype SlackerClient [conn content-type]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params]
    (let [fname (str ns-name "/" func-name)
          request (make-request content-type fname params)
          response (wait-for-result (conn request) *timeout*)]
      (when-let [[_ resp] response]
        (handle-response resp))))
  (async-call-remote [this ns-name func-name params cb]
    (let [fname (str ns-name "/" func-name)
          request (make-request content-type fname params)]
      (run-pipeline
       (conn request)
       #(if-let [[_ resp] %]
          (let [result (handle-response resp)]
            (if-not (nil? cb) (cb result))
            result)))))
  (inspect [this cmd args]
    (let [request (make-inspect-request cmd args)
          response (wait-for-result (conn request) *timeout*)]
      (parse-inspect-response response)))
  (close [this]
    (close-connection conn)))

(defn invoke-slacker
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [sc remote-call-info
   & {:keys [async? callback]
      :or {async? false callback nil}}]
  (let [[nsname fname args] remote-call-info]
    (if (or async? (not (nil? callback)))
      (async-call-remote sc nsname fname args callback)
      (sync-call-remote sc nsname fname args))))

(defn meta-remote
  "get metadata of a remote function by inspect api"
  [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (inspect sc :meta fname)))

(defn host-port
  "get host and port from connection string"
  [connection-string]
  (let [[host port] (split connection-string #":")]
    [host (Integer/valueOf port)]))

