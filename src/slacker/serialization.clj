(ns slacker.serialization
  (:use [slacker common])
  (:require [carbonite.api :as carb])
  (:require [cheshire.core :as json])
  (:import [java.nio ByteBuffer])
  (:import [java.nio.charset Charset]))

(def carb-registry (atom (carb/default-registry)))

(defmulti serialize
  "serialize clojure data structure to bytebuffer with
  different types of serialization"
  (fn [f _] f))
(defmulti deserialize
  "deserialize clojure data structure from bytebuffer using
  matched serialization function"
  (fn [f _] f))

(defmethod deserialize :carb
  [_ data]
  (carb/read-buffer @carb-registry data))

(defmethod serialize :carb
  [_ data]
  (ByteBuffer/wrap (carb/write-buffer @carb-registry data)))

(defn register-serializers
  "Register additional serializers to carbonite. This allows
  slacker to transport custom data types. Caution: you should
  register serializers on both server side and client side."
  [serializers]
  (swap! carb-registry carb/register-serializers serializers))

(defmethod deserialize :json
  [_ data]
  (let [jsonstr (.toString (.decode (Charset/forName "UTF-8") data))]
    (if *debug* (println (str "dbg:: " jsonstr)))
    (json/parse-string jsonstr true)))

(defmethod serialize :json
  [_ data]
  (let [jsonstr (json/generate-string data)]
    (if *debug* (println (str "dbg:: " jsonstr)))
    (.encode (Charset/forName "UTF-8") jsonstr)))

(defmethod deserialize :clj
  [_ data]
  (let [cljstr (.toString (.decode (Charset/forName "UTF-8") data))]
    (if *debug* (println (str "dbg:: " cljstr)))
    (read-string cljstr)))

(defmethod serialize :clj
  [_ data]
  (let [cljstr (pr-str data)]
    (if *debug* (println (str "dbg:: " cljstr)))
    (.encode (Charset/forName "UTF-8") cljstr)))


