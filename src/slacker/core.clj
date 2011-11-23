(ns slacker.core
  (:use [gloss.core])
  (:use [aleph.core])
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

(def version 1)
(def type-request 1)
(def type-response 2)

(defn- read-carb [data]
  (carb/read-buffer carb-registry data))

(defn- write-carb [data]
  (carb/write-buffer carb-registry data))

(defn server-handler [ch client-info]
  (receive-all ch
               #(if-let [[version type func-name data] %]
                  (let [params (read-carb data)
                        f (find-func funcname)
                        r (apply f params)]
                    (enqueue ch [version type-response func-name (write-carb r)])))))

(defn client-handler [ch func-name params]
  (enqueue ch [version type-request func-name (write-carb params)])
  (receive-all ch
               #(if-let [[version type func-name data] %]
                  (read-carb data))))



