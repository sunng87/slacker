(ns slacker.common
  (:use gloss.core)
  (:require [carbonite.api :as carb])
  (:import [java.nio ByteBuffer]))

;; slacker protocol: request
;;
;; | version | packettype | <-- string length -->|
;; | <---- ..func.. ---->                        |
;; | <--- byte length --->| <---- ..body.. ----> |
;; 
;;
(defcodec slacker-request-codec
  [:byte
   :byte
   (finite-frame :int16 (string :utf8))
   (finite-block :int16)])

;; slacker protocol: response
;;
;; | version | packettype | response-code | <--  |
;; | byte len| <-- body -->                      |
;;
;;
;;
(defcodec slacker-response-codec
  [:byte
   :byte
   :byte
   (finite-block :int16)])

(def carb-registry (carb/default-registry))

(def *debug* true)
(def *timeout* 10000)
(def version (short 1))
(def type-request (short 1))
(def type-response (short 1))

(def result-code-success (short 0))
(def result-code-notfound (short 10))
(def result-code-exception (short 20))

(defn read-carb [data]
  (carb/read-buffer carb-registry data))

(defn write-carb [data]
  (ByteBuffer/wrap (carb/write-buffer carb-registry data)))


