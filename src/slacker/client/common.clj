(ns slacker.client.common
  (:refer-clojure :exclude [send])
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common protocol])
  (:use [link.core :exclude [close]])
  (:use [link.tcp])
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure.tools.logging :as log])
  (:import [java.net ConnectException])
  (:import [java.nio.channels ClosedChannelException]))

(defn- handle-valid-response [response]
  (let [[content-type code data] (second response)]
    (case code
      :success (deserialize content-type data)
      :not-found (throw+ {:code code})
      :exception (let [einfo (deserialize content-type data)]
                   (if-not (map? einfo)
                     (throw+ {:code code :error einfo})
                     (let [e (Exception. ^String (:msg einfo))]
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

(defn make-inspect-request [tid cmd args]
  [version tid [:type-inspect-req
                [cmd (serialize :clj args :string)]]])
(defn parse-inspect-response [response]
  (deserialize :clj (-> response
                        second
                        first)
               :string))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params])
  (async-call-remote [this ns-name func-name params cb])
  (inspect [this cmd args])
  (close [this]))

(deftype SlackerClient [conn rmap trans-id-gen content-type ob-init ob-max]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params]
    (binding [*ob-init* ob-init
              *ob-max* ob-max]
      (let [fname (str ns-name "/" func-name)
            tid (swap! trans-id-gen inc)
            request (make-request tid content-type fname params)
            prms (promise)]
        (swap! rmap assoc tid {:promise prms})
        (send conn request)
        (deref prms *timeout* nil)
        (if (realized? prms)
          (handle-response @prms)
          (do
            (swap! rmap dissoc tid)
            (throw+ {:error :timeout}))))))
  (async-call-remote [this ns-name func-name params cb]
    (binding [*ob-init* ob-init
              *ob-max* ob-max]
      (let [fname (str ns-name "/" func-name)
            tid (swap! trans-id-gen inc)
            request (make-request tid content-type fname params)
            prms (promise)]
        (swap! rmap assoc tid {:promise prms :callback cb :async? true})
        (send conn request)
        prms)))
  (inspect [this cmd args]
    (binding [*ob-init* ob-init
              *ob-max* ob-max]
      (let [tid (swap! trans-id-gen inc)
            request (make-inspect-request tid cmd args)
            prms (promise)]
        (swap! rmap assoc tid {:promise prms :type :inspect})
        (send conn request)
        (deref prms *timeout* nil)
        (if (realized? prms)
          (parse-inspect-response @prms)))))
  (close [this]
    (link.core/close conn)))

(defn- create-link-handler
  "The event handler for client"
  [rmap]
  (create-handler
   (on-message [_ msg _]
               (let [tid (second msg)
                     callback (get @rmap tid)]
                 (swap! rmap dissoc tid)
                 (when-not (nil? callback)
                   (let [msg-body (nth msg 2)]
                     (if (:async? callback)
                       ;; async callback should run in another thread
                       ;; that won't block or hang the worker thread
                       (future
                         (let [result (handle-response msg-body)]
                           (deliver (:promise callback) result)
                           (if-let [cb (:callback callback)]
                             (cb result))))
                       ;; sync request need to decode ths message in 
                       ;; caller thread
                       (deliver (:promise callback) msg-body))))))
   (on-error [_ ^Exception exc]
             (if (or
                  (instance? ConnectException exc)
                  (instance? ClosedChannelException exc))
               ;; remove all pending requests
               (do
                 (log/warn "Failed to connect to server or connection lost.")
                 (reset! rmap {}))
               (log/error exc "Unexpected error in event loop")))))

(def tcp-options
  {"tcpNoDelay" true,
   "reuseAddress" true,
   "readWriteFair" true,
   "connectTimeoutMillis" 3000})

(defn create-client [host port content-type]
  (let [rmap (atom  {})
        handler (create-link-handler rmap)
        client (tcp-client host port handler
                           :codec slacker-base-codec
                           :tcp-options tcp-options)]
    ; Passing in currently bound values of *ob-init*, *ob-max*
    (SlackerClient. client rmap (atom 0) content-type *ob-init* *ob-max*)))

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
    [host (Integer/valueOf ^String port)]))

