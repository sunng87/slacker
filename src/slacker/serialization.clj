(ns slacker.serialization
  (:require [clojure.tools.logging :as logging]
            [clojure.java.io :refer [copy]]
            [slacker.common :refer :all]
            [clojure.edn :as edn])
  (:import [java.nio.charset Charset]
           [io.netty.buffer ByteBuf ByteBufAllocator ByteBufInputStream
            ByteBufOutputStream]))

(defn- bytebuffer-bytes [^ByteBuffer data]
  (let [bs (byte-array (.remaining data))]
    (.get data bs)
    bs))

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
      (parse-string (.toString data ^Charset (Charset/forName "UTF-8")) true))

    (defmethod serialize :json
      [_ ^ByteBufAllocator alloc data]
      (let [jsonstr (generate-string data)
            bytes (.getBytes ^String jsonstr "UTF-8")
            bytes-length (alength bytes)
            buffer (.buffer alloc bytes-length)]
        (.writeBytes buffer bytes)
        buffer)))

  (catch Throwable _
    (logging/info  "Disable cheshire (json) support.")))


(defmethod deserialize :clj
  [_ ^ByteBuf data]
  (edn/read-string (.toString data ^Charset (Charset/forName "UTF-8"))))

(defmethod serialize :clj
  [_ ^ByteBufAllocator alloc data]
  (let [ednstr (pr-str data)
        bytes (.getBytes ^String ednstr "UTF-8")
        bytes-length (alength bytes)
        buffer (.buffer alloc bytes-length)]
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
      (let [bin (ByteBufInputStream. data)]
        (thaw bin)))

    (defmethod serialize :nippy
      [_ ^ByteBufAllocator alloc data]
      (let [buffer (.buffer alloc)
            bos (ByteBufOutputStream. buffer)]
        (freeze bos data)
        buffer)))

  (catch Throwable _
    (logging/info "Disable nippy support.")))
