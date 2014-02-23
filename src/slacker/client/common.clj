(ns slacker.client.common
  (:refer-clojure :exclude [send])
  (:use [clojure.string :only [split]])
  (:use [slacker serialization common protocol])
  (:use [link.core :exclude [close]])
  (:use [link.tcp])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:require [clojure.tools.logging :as log])
  (:import [java.net ConnectException]
           [java.nio.channels ClosedChannelException]
           [java.util.concurrent ScheduledThreadPoolExecutor
            TimeUnit ScheduledFuture]))

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

(defn handle-response [response]
  (case (first response)
    :type-response (handle-valid-response response)
    :type-inspect-ack (parse-inspect-response response)
    :type-pong (log/debug "pong")
    :type-error (throw+ {:code (-> response second first)})
    nil))

(defprotocol SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params options])
  (async-call-remote [this ns-name func-name params cb options])
  (inspect [this cmd args])
  (ping [this])
  (close [this]))

(defn- next-trans-id [trans-id-gen]
  (swap! trans-id-gen unchecked-inc))

(deftype SlackerClient [conn rmap trans-id-gen content-type]
  SlackerClientProtocol
  (sync-call-remote [this ns-name func-name params options]
    (let [fname (str ns-name "/" func-name)
          tid (next-trans-id trans-id-gen)
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms})
      (send conn request)
      (deref prms *timeout* nil)
      (if (realized? prms)
        @prms
        (do
          (swap! rmap dissoc tid)
          (throw+ {:error :timeout})))))
  (async-call-remote [this ns-name func-name params cb options]
    (let [fname (str ns-name "/" func-name)
          tid (next-trans-id trans-id-gen)
          request (make-request tid content-type fname params)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms :callback cb :async? true})
      (send conn request)
      prms))
  (inspect [this cmd args]
    (let [tid (next-trans-id trans-id-gen)
          request (make-inspect-request tid cmd args)
          prms (promise)]
      (swap! rmap assoc tid {:promise prms :type :inspect})
      (send conn request)
      (deref prms *timeout* nil)
      (if (realized? prms)
        @prms
        (do
          (swap! rmap dissoc tid)
          (throw+ {:error :timeout})))))
  (ping [this]
    (send conn ping-packet)
    (log/debug "ping"))
  (close [this]
    (link.core/close conn)))

(defn- create-link-handler
  "The event handler for client"
  [rmap]
  (create-handler
   (on-message [_ msg]
               (let [tid (second msg)
                     msg-body (nth msg 2)
                     callback (get @rmap tid)]
                 (swap! rmap dissoc tid)
                 (if-not (nil? callback)
                   (if (:async? callback)
                     ;; async callback should run in another thread
                     ;; that won't block or hang the worker thread
                     (future
                       (let [result (handle-response msg-body)]
                         (deliver (:promise callback) result)
                         (when-let [cb (:callback callback)]
                           (cb result))))
                     ;; sync request need to decode ths message in
                     ;; caller thread
                     (deliver (:promise callback)
                              (handle-response msg-body)))
                   (handle-response msg-body))))
   (on-error [_ ^Exception exc]
             (println 123)
             (if (or
                  (instance? ConnectException exc)
                  (instance? ClosedChannelException exc))
               ;; remove all pending requests
               (do
                 (log/warn "Failed to connect to server or connection lost.")
                 (reset! rmap {}))
               (log/error exc "Unexpected error in event loop")))))

(def ^:dynamic *options*
  {:tcp-nodelay true
   :so-reuseaddr true
   :so-keepalive true
   :write-buffer-high-water-mark (int 0xFFFF) ; 65kB
   :write-buffer-low-water-mark (int 0xFFF)       ; 4kB
   :connect-timeout-millis (int 5000)})

(defonce request-map (atom {}));; shared between multiple connections
(defonce transaction-id-counter (atom 0))

(defn create-client-factory [ssl-context]
  (let [handler (create-link-handler request-map)]
    (tcp-client-factory handler
                        :codec slacker-base-codec
                        :options *options*
                        :ssl-context ssl-context)))

(defn create-client [client-factory host port content-type]
  (let [client (tcp-client client-factory host port)]
    (SlackerClient. client request-map transaction-id-counter content-type)))

(def ^:dynamic *sc* nil)
(defn invoke-slacker
  "Invoke remote function with given slacker connection.
  A call-info tuple should be passed in. Usually you don't use this
  function directly. You should define remote call facade with defremote"
  [sc remote-call-info
   & {:keys [async? callback]
      :or {async? false callback nil}
      :as options}]
  (let [sc @(or *sc* sc) ;; allow local binding to override client
        [nsname fname args] remote-call-info]
    (if (or async? (not (nil? callback)))
      (async-call-remote sc nsname fname args callback options)
      (sync-call-remote sc nsname fname args options))))

(defn meta-remote
  "get metadata of a remote function by inspect api"
  [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (inspect @sc :meta fname)))

(defn host-port
  "get host and port from connection string"
  [connection-string]
  (let [[host port] (split connection-string #":")]
    [host (Integer/valueOf ^String port)]))

(defonce scheduled-clients (atom {}))
(defonce schedule-pool
  (ScheduledThreadPoolExecutor.
   (.availableProcessors (Runtime/getRuntime))))

(defn schedule-ping [delayed-client interval]
  (let [cancelable (.scheduleAtFixedRate
                    ^ScheduledThreadPoolExecutor schedule-pool
                    #(try+
                       (when (realized? delayed-client)
                         (ping @delayed-client))
                       (catch Exception e
                         (.printStackTrace e)))
                    0 ;; initial delay
                    interval
                    TimeUnit/SECONDS)]
    (swap! scheduled-clients assoc delayed-client cancelable)
    cancelable))

(defn cancel-ping [client]
  (when-let [cancelable (@scheduled-clients client)]
    (.cancel ^ScheduledFuture cancelable true)))
