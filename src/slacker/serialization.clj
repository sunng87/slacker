(ns slacker.serialization
  (:use [slacker common])
  (:use [slacker.serialization.carbonite])
  (:use [clojure.java.io :only [copy]])
  (:require [carbonite.api :as carb])
  (:require [cheshire.core :as json])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream])
  (:import [java.nio ByteBuffer])
  (:import [java.nio.charset Charset])
  (:import [java.util.zip DeflaterInputStream InflaterInputStream]))

(defn- bytebuffer-bytes [^ByteBuffer data]
  (let [bs (byte-array (.remaining data))]
    (.get data bs)
    bs))

(defmulti serialize
  "serialize clojure data structure to bytebuffer with
  different types of serialization"
  (fn [f _ & _] (if (.startsWith (name f) "deflate")
                 :deflate f)))
(defmulti deserialize
  "deserialize clojure data structure from bytebuffer using
  matched serialization function"
  (fn [f _ & _] (if (.startsWith (name f) "deflate")
                 :deflate f)))

(defmethod deserialize :carb
  ([_ data] (deserialize :carb data :buffer))
  ([_ data it]
     (if (= it :buffer)
       (carb/read-buffer @carb-registry data)
       (deserialize :carb (ByteBuffer/wrap data) :buffer))))

(defmethod serialize :carb
  ([_ data] (serialize :carb data :buffer))
  ([_ data ot]
     (if (= ot :bytes)
       (carb/write-buffer @carb-registry data)
       (ByteBuffer/wrap (serialize :carb data :bytes)))))


(defmethod deserialize :json
  ([_ data] (deserialize :json data :buffer))
  ([_ data it]
     (let [jsonstr
           (case it
             :buffer (.toString (.decode (Charset/forName "UTF-8") data))
             :bytes (String. ^bytes ^String data "UTF-8")
             :string data)]
       (if *debug* (println (str "dbg:: " jsonstr)))
       (json/parse-string jsonstr true))))

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
  ([_ data] (deserialize :clj data :buffer))
  ([_ data ot]
     (let [cljstr
           (case ot
             :buffer (.toString (.decode (Charset/forName "UTF-8") data))
             :bytes (String. ^bytes ^String data "UTF-8")
             :string data)]
       (if *debug* (println (str "dbg:: " cljstr)))
       (read-string cljstr))))

(defmethod serialize :clj
  ([_ data] (serialize :clj data :buffer))
  ([_ data ot]
     (let [cljstr (pr-str data)]
       (if *debug* (println (str "dbg:: " cljstr)))
       (case ot
         :buffer (.encode (Charset/forName "UTF-8") cljstr)
         :string cljstr
         :bytes (.getBytes cljstr "UTF-8")))))


(defmethod serialize :deflate
  ([dct data] (serialize dct data :buffer))
  ([dct data ot]
     (let [ct (keyword (subs (name dct) 8))
           sdata (serialize ct data :bytes)
           deflater (DeflaterInputStream.
                      (ByteArrayInputStream. sdata))
           out-s (ByteArrayOutputStream.)
           out-bytes (do
                       (copy deflater out-s)
                       (.toByteArray out-s))]
       (case ot
         :buffer (ByteBuffer/wrap out-bytes)
         :bytes out-bytes))))

(defmethod deserialize :deflate
  ([dct data] (deserialize dct data :buffer))
  ([dct data ot]
     (let [ct (keyword (subs (name dct) 8))
           in-bytes (case ot
                      :buffer (bytebuffer-bytes data) 
                      :bytes data)
           inflater (InflaterInputStream.
                     (ByteArrayInputStream. in-bytes))
           out-s (ByteArrayOutputStream.)
           out-bytes (do
                       (copy inflater out-s)
                       (.toByteArray out-s))]
       (deserialize ct out-bytes :bytes))))

