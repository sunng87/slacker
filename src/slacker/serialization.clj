(ns slacker.serialization
  (:use [slacker common])
  (:require [carbonite.api :as carb])
  (:require [clj-json.core :as json])
  (:import [java.nio ByteBuffer])
  (:import [java.nio.charset Charset]))

(def carb-registry (atom (carb/default-registry)))

(defmulti serialize
  "serialize clojure data structure to bytebuffer with
  different types of serialization"
  (fn [f _ & _] f))
(defmulti deserialize
  "deserialize clojure data structure from bytebuffer using
  matched serialization function"
  (fn [f _] f))

(defmethod deserialize :carb
  [_ data]
  (carb/read-buffer @carb-registry data))

(defmethod serialize :carb
  ([_ data] (serialize :carb data :buffer))
  ([_ data ot]
     (if (= ot :bytes)
       (carb/write-buffer @carb-registry data)
       (ByteBuffer/wrap (carb/write-buffer @carb-registry data)))))

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
  ([_ data] (serialize :json data :buffer))
  ([_ data ot]
     (let [jsonstr (json/generate-string data)]
       (if *debug* (println (str "dbg:: " jsonstr)))
       (case ot
         :buffer (.encode (Charset/forName "UTF-8") jsonstr)
         :string jsonstr
         :bytes (.getBytes jsonstr "UTF-8")))))

(defmethod deserialize :clj
  [_ data]
  (let [cljstr (.toString (.decode (Charset/forName "UTF-8") data))]
    (if *debug* (println (str "dbg:: " cljstr)))
    (read-string cljstr)))

(defmethod serialize :clj
  ([_ data] (serialize :clj data :buffer))
  ([_ data ot]
     (let [cljstr (pr-str data)]
       (if *debug* (println (str "dbg:: " cljstr)))
       (case ot
         :buffer (.encode (Charset/forName "UTF-8") cljstr)
         :string cljstr
         :bytes (.getBytes cljstr "UTF-8")))))


