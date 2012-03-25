(ns slacker.client.common
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common])
  (:use [link core tcp])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- handle-valid-response [response]
  (let [[content-type code data] (second response)]
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
    :type-response (handle-valid-response response)
    :type-error (throw+ {:code (-> response second first)})
    nil))

(defn make-request [tid content-type func-name params]
  (let [serialized-params (serialize content-type params)]
    [version tid [:type-request [content-type func-name serialized-params]]]))

(def ping-packet [version 0 [:type-ping]])
(defn ping [conn]
  (wait-for-result (conn ping-packet) *timeout*))

(defn make-inspect-request [tid cmd args]
  [version tid [:type-inspect-req
                [cmd (serialize :clj args :string)]]])
(defn parse-inspect-response [response]
  (deserialize :clj (-> response
                        (nth 2)
                        second
                        first)
               :string))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params])
  (async-call-remote [this ns-name func-name params cb])
  (inspect [this cmd args])
  (close [this]))

(deftype SlackerClient [conn rmap trans-id-gen content-type]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params]
    (let [fname (str ns-name "/" func-name)
          tid (swap! trans-id-gen inc)
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms :type :call})
      (.write conn request)
      (deref prms *timeout* nil)
      (if (realized? prms)
        @prms
        (do
          (swap! rmap dissoc tid)
          (throw+ {:error :timeout})))))
  (async-call-remote [this ns-name func-name params cb]
    (let [fname (str ns-name "/" func-name)
          tid (swap! trans-id-gen inc)
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms :callback cb :type :call})
      (.write conn request)
      prms))
  (inspect [this cmd args]
    (let [tid (swap! trans-id-gen inc)
          request (make-inspect-request tid cmd args)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms :type :inspect})
      (.write conn request)
      (deref prms *timeout* nil)
      (if (realized? prms)
        @prms)))
  (close [this]
    (.close conn)))

(defn- create-link-handler
  "The event handler for client"
  [rmap]
  (create-handler
   (on-message [ctx e]
               (let [msg (.getMessage e)
                     tid (second msg)
                     callback (get @rmap tid)]
                 (swap! rmap dissoc tid)
                 (when-not (nil? callback)
                   (let [msg-body (nth msg 2)
                         result (case (:type callback)
                                  :call (handle-response msg-body)
                                  :inspect (parse-inspect-response
                                            msg-body))]
                     (if-let [prms (:promise callback)]
                       (deliver prms result))
                     (if-let [cb (:callback callback)]
                       (cb result))))))))

(defn create-client [host port content-type]
  (let [rmap (atom  {})
        handler (create-link-handler rmap)
        client (tcp-client host port handler)]
    (SlackerClient. client rmap (atom 0) content-type)))

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

