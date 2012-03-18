(ns slacker.serialization.carbonite
  (:require [carbonite.api :as carb])
  (:import [java.nio ByteBuffer])
  (:import [com.esotericsoftware.kryo Kryo Serializer SerializationException]
           [com.esotericsoftware.kryo.serialize StringSerializer
            MapSerializer IntSerializer
            LongSerializer BigDecimalSerializer BigIntegerSerializer
            DateSerializer]))

(def stacktrace-element-serializer
  (proxy [Serializer] []
    (writeObjectData [buffer ^StackTraceElement v]
      (StringSerializer/put buffer (.getClassName v))
      (StringSerializer/put buffer (.getMethodName v))
      (StringSerializer/put buffer (.getFileName v))
      (IntSerializer/put buffer (.getLineNumber v) true))
    (readObjectData [^ByteBuffer buffer type]
      (let [class-name (StringSerializer/get buffer)
            method-name (StringSerializer/get buffer)
            file-name (StringSerializer/get buffer)
            line-number (IntSerializer/get buffer true)]
        (StackTraceElement. class-name method-name file-name line-number)))))

(def carb-registry (atom (carb/default-registry)))

(defn register-serializers
  "Register additional serializers to carbonite. This allows
  slacker to transport custom data types. Caution: you should
  register serializers on both server side and client side."
  [serializers]
  (swap! carb-registry carb/register-serializers serializers))

(register-serializers {StackTraceElement stacktrace-element-serializer})
  
