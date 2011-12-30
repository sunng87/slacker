(ns slacker.client.common
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
      :exception (throw+ {:code code :error (deserialize content-type (contiguous data))})
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

(defprotocol SlackerClientProtocol
  (sync-call-remote [this func-name params])
  (async-call-remote [this func-name params cb])
  (close [this]))

(deftype SlackerClient [conn content-type]
  SlackerClientProtocol
  (sync-call-remote [this func-name params]
    (let [request (make-request content-type func-name params)
          response (wait-for-result (conn request) *timeout*)]
      (when-let [[_ resp] response]
        (handle-response resp))))
  (async-call-remote [this func-name params cb]
    (let [request (make-request content-type func-name params)]
      (run-pipeline
       (conn request)
       #(if-let [[_ resp] %]
          (let [result (handle-response resp)]
            (if-not (nil? cb) (cb result))
            result)))))
  (close [this]
    (close-connection conn)))


