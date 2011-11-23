(ns slacker.core
  (:use [gloss.core])
  (:use [aleph.tcp])
  (:use [lamina.core])
  (:require [carbonite.api :as carb]))

;; slacker protocol
;;
;; | version | packettype | <-- string length -->|
;; | <---- ..func.. ---->                        |
;; | <--- byte length --->| <---- ..body.. ----> |
;; 
;;
(defcodec slacker-codec
  [:byte
   :byte
   (finite-frame :int16 (string :utf8))
   (finite-block :int16)])

(def carb-registry (carb/default-registry))

(def *debug* true)
(def version 1)
(def type-request 1)
(def type-response 2)

(def functions (atom {}))

(defn- read-carb [data]
  (carb/read-buffer carb-registry data))

(defn- write-carb [data]
  (carb/write-buffer carb-registry data))

(defn not-found [& args] "function not found")
(defn create-server-handler [funcs]
  (fn [ch client-info]
    (receive-all ch
               #(if-let [[version type fname data] %]
                  (let [params (read-carb data)
                        f (get funcs fname not-found)
                        r (apply f params)]
                    (enqueue ch [version type-response fname (write-carb r)]))))))

(defn start-slacker-server [exposed-ns port]
  (let [funcs (into {} (for [f (ns-publics exposed-ns)] [(name (key f)) (val f)]))
        handler (create-server-handler funcs)]
    (when *debug* (doseq [f (keys funcs)] (println f)))
    (start-tcp-server handler {:port port :frame slacker-codec})))



(defn- call-remote [ch func-name params]
  (enqueue ch [version type-request func-name (write-carb params)])
  (receive-all ch
               #(if-let [[version type func-name data] %]
                  (read-carb data))))
(defn slacker-client [host port]
  (tcp-client {:host host :port port :frame slacker-codec}))

(defn with-slacker-client [channel & remote-call-info]
  (doseq [[fname args] remote-call-info]
    (call-remote channel fname args)))

(defmacro defremote [fname]
  `(defn ~fname [& args#]
     [(name '~fname) args#]))

