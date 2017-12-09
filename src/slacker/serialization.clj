(ns slacker.serialization
  (:require [clojure.tools.logging :as logging]
            [clojure.java.io :refer [copy]]
            [slacker.common :refer :all]
            [clojure.edn :as edn])
  (:import [java.nio.charset Charset]
           [io.netty.buffer ByteBuf ByteBufAllocator ByteBufInputStream
            ByteBufOutputStream]))

(defn- resolve-by-name [ns mem]
  @(ns-resolve (symbol ns) (symbol mem)))

(defmulti serialize
  "serialize clojure data structure to bytebuf with
  different types of serialization"
  (fn [f _ & _] f))
(defmulti deserialize
  "deserialize clojure data structure from bytebuf using
  matched serialization function"
  (fn [f _ & _] f))

(try
  (require 'cheshire.core)

  (let [parse-string (resolve-by-name "cheshire.core" "parse-string")
        generate-string (resolve-by-name "cheshire.core" "generate-string")]
    (defmethod deserialize :json
      [_ ^ByteBuf data]
      (let [s (.toString data ^Charset (Charset/forName "UTF-8"))]
        (.release data)
        (parse-string s true)))

    (defmethod serialize :json
      [_ data]
      (let [jsonstr (generate-string data)
            bytes (.getBytes ^String jsonstr "UTF-8")
            bytes-length (alength bytes)
            buffer (.buffer ByteBufAllocator/DEFAULT bytes-length)]
        (.writeBytes buffer bytes)
        buffer)))

  (catch Throwable _
    (logging/info  "Disable cheshire (json) support.")))


(defmethod deserialize :clj
  [_ ^ByteBuf data]
  (let [s (.toString data ^Charset (Charset/forName "UTF-8"))]
    (.release data)
    (edn/read-string s)))

(defmethod serialize :clj
  [_ data]
  (let [ednstr (pr-str data)
        bytes (.getBytes ^String ednstr "UTF-8")
        bytes-length (alength bytes)
        buffer (.buffer ByteBufAllocator/DEFAULT bytes-length)]
    (.writeBytes buffer bytes)
    buffer))

(try
  (require '[taoensso.nippy])
  (try
    (require '[slacker.serialization.nippy])
    (catch Throwable _
      (logging/info "Nippy version below 2.7.1. Disable stacktrace transfer support")))

  (let [thaw (resolve-by-name "taoensso.nippy" "thaw-from-in!")
        freeze (resolve-by-name "taoensso.nippy" "freeze-to-out!")]

    (defmethod deserialize :nippy
      [_ ^ByteBuf data]
      (let [bin (ByteBufInputStream. data)
            r (thaw bin)]
        (.release data)
        r))

    (defmethod serialize :nippy
      [_ data]
      (let [buffer (.buffer ByteBufAllocator/DEFAULT)
            bos (ByteBufOutputStream. buffer)]
        (freeze bos data)
        buffer)))

  (catch Throwable _
    (logging/info "Disable nippy support.")))

(try
  (require '[cognitect.transit])
  (let [write (resolve-by-name "cognitect.transit" "write")
        writer (resolve-by-name "cognitect.transit" "writer")
        read (resolve-by-name "cognitect.transit" "read")
        reader (resolve-by-name "cognitect.transit" "reader")]

    (defmethod deserialize :transit-json
      [_ ^ByteBuf data]
      (let [bin (ByteBufInputStream. data)
            r (read (reader bin :json))]
        (.release data)
        r))

    (defmethod serialize :transit-json
      [_ data]
      (let [buffer (.buffer ByteBufAllocator/DEFAULT)
            bos (ByteBufOutputStream. buffer)]
        (write (writer bos :json) data)
        buffer))

    (defmethod deserialize :transit-msgpack
      [_ ^ByteBuf data]
      (let [bin (ByteBufInputStream. data)
            r (read (reader bin :msgpack))]
        (.release data)
        r))

    (defmethod serialize :transit-msgpack
      [_ data]
      (let [buffer (.buffer ByteBufAllocator/DEFAULT)
            bos (ByteBufOutputStream. buffer)]
        (write (writer bos :msgpack) data)
        buffer)))

  (catch Throwable _
    (logging/info "Disable transit support.")))
