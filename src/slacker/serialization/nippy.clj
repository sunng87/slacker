(ns ^:no-doc slacker.serialization.nippy
  (:require [taoensso.nippy :as nippy])
  (:import [java.io DataOutput DataInput]))

(nippy/extend-freeze StackTraceElement :slacker/exception
                     [^StackTraceElement s ^DataOutput data-output]
                     (.writeUTF data-output (.getClassName s))
                     (.writeUTF data-output (.getMethodName s))
                     (.writeUTF data-output (.getFileName s))
                     (.writeInt data-output (.getLineNumber s)))

(nippy/extend-thaw :slacker/exception
                   [^DataInput data-input]
                   (StackTraceElement.
                    (.readUTF data-input)
                    (.readUTF data-input)
                    (.readUTF data-input)
                    (.readInt data-input)))
