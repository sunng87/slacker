(ns slacker.serialization.nippy
  (:require [taoensso.nippy :as nippy])
  (:import [java.io DataOutput DataInput]))

(nippy/extend-freeze StackTraceElement 1
                     [^StackTraceElement s ^DataOutput data-output]
                     (.writeUTF data-output (.getClassName s))
                     (.writeUTF data-output (.getMethodName s))
                     (.writeUTF data-output (.getFileName s))
                     (.writeInt data-output (.getLineNumber s)))

(nippy/extend-thaw 1
                   [^DataInput data-input]
                   (StackTraceElement.
                    (.readUTF data-input)
                    (.readUTF data-input)
                    (.readUTF data-input)
                    (.readInt data-input)))
