(ns slacker.common
  (:use gloss.core)
  (:require [carbonite.api :as carb])
  (:import [java.nio ByteBuffer]))

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
(def *timeout* 10000)
(def version (short 1))
(def type-request (short 1))
(def type-response (short 1))

(defn read-carb [data]
  (carb/read-buffer carb-registry data))

(defn write-carb [data]
  (ByteBuffer/wrap (carb/write-buffer carb-registry data)))


