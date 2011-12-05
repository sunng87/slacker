(ns slacker.common
  (:use gloss.core)
  (:require [carbonite.api :as carb])
  (:require [clj-json.core :as json])
  (:import [java.nio ByteBuffer]))

(defcodec slacker-request-codec
  [:byte ;; protocol version
   :byte ;; packet-type
   :byte ;; content-type
   (finite-frame :int16 (string :utf8)) ;; function name
   (finite-block :int16) ;; arguments
   ])

(defcodec slacker-response-codec
  [:byte ;; protocol version
   :byte ;; packet-type
   :byte ;; content-type
   :byte ;; result code
   (finite-block :int16) ;; result data
   ])

(def carb-registry (atom (carb/default-registry)))

(def *debug* false)
(def *timeout* 10000)
(def version (short 2))
(def type-request (short 0))
(def type-response (short 1))
(def content-type-carb (short 0))
(def content-type-json (short 1))

(def result-code-success (short 0))
(def result-code-notfound (short 11))
(def result-code-exception (short 12))
(def result-code-version-mismatch (short 13))

(defn read-carb
  "Deserialize clojure data structure from ByteBuffer with
  carbonite."
  [data]
  (carb/read-buffer @carb-registry data))

(defn write-carb
  "Serialize clojure data structure to ByteBuffer by carbonite"
  [data]
  (ByteBuffer/wrap (carb/write-buffer @carb-registry data)))

(defn register-serializers
  "Register additional serializers to carbonite. This allows
  slacker to transport custom data types. Caution: you should
  register serializers on both server side and client side."
  [serializers]
  (swap! carb-registry carb/register-serializers serializers))

(defn- key-to-keyword [m]
  (into {} (for [e m]
             [(if (string? (key e))
                (keyword (key e))
                (key e))
              (val e)])))

(defn read-json
  "Deserialize clojure data structure from bytebuffer with
  jackson"
  [data]
  (json/parse-string (String. (.array data) "UTF-8") true))

(defn write-json
  "Serialize clojure data structure to ByteBuffer with jackson"
  [data]
  (ByteBuffer/wrap (.getBytes (json/generate-string data) "UTF-8")))

