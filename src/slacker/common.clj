(ns slacker.common
  (:use gloss.core)
  (:require [carbonite.api :as carb])
  (:require [clj-json.core :as json])
  (:import [java.nio ByteBuffer])
  (:import [java.nio.charset Charset]))

(defcodec packet-type
  (enum :byte {:type-request 0
               :type-response 1
               :type-ping 2
               :type-pong 3}))

(defcodec content-type
  (enum :byte {:carb 0 :json 1}))

(defcodec result-codes
  (enum :byte {:success 0
               :not-found 11
               :exception 12
               :protocol-mismatch 13}))

(defcodec slacker-request-codec
  [:byte ;; protocol version
   packet-type ;; packet-type
   content-type ;; content-type
   (finite-frame :int16 (string :utf8)) ;; function name
   (finite-block :int16) ;; arguments
   ])

(defcodec slacker-response-codec
  [:byte ;; protocol version
   packet-type ;; packet-type
   content-type ;; content-type
   result-codes ;; result code
   (finite-block :int16) ;; result data
   ])

(def carb-registry (atom (carb/default-registry)))

(def *debug* false)
(def *timeout* 10000)
(def version (short 2))

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
  (let [jsonstr (.toString (.decode (Charset/forName "UTF-8") data))]
    (if *debug* (println (str "dbg:: " jsonstr)))
    (json/parse-string jsonstr true)))

(defn write-json
  "Serialize clojure data structure to ByteBuffer with jackson"
  [data]
  (let [jsonstr (json/generate-string data)]
    (if *debug* (println (str "dbg:: " jsonstr)))
    (.encode (Charset/forName "UTF-8") jsonstr)))

(defn deserializer
  "Find certain deserializer by content-type code:
  * 0-carbonite,
  * 1-json"
  [type]
  (case type
    :json read-json
    :carb read-carb))

(defn serializer
  "Find certain serializer by content-type code:
  * 0-carbonite,
  * 1-json"
  [type]
  (case type
    :json write-json
    :carb write-carb))

