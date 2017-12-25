(ns slacker.common
  (:require [trptcolin.versioneer.core :as ver]))

(def
  ^{:doc "Debug flag. This flag can be override by binding if you like to see some debug output."
    :dynamic true}
  *debug* false)
(def
  ^{:doc "Timeout for synchronouse call."
    :dynamic true}
  *timeout* 10000)

(def
  ^{:doc "Initial Kryo ObjectBuffer size (bytes)."
    :dynamic true}
  *ob-init* (* 1024 1))

(def
  ^{:doc "Maximum Kryo ObjectBuffer size (bytes)."
    :dynamic true}
  *ob-max* (* 1024 16))

(def
  ^{:doc "Max on-the-fly requests the client can have. Set to 0 to disable flow control."
    :dynamic true}
  *backlog* 5000)

(def
  ^{:doc "Request extension map, keyed by an integer as extension id."
    :dynamic true}
  *extensions* {})

(defmacro with-extensions
  "Setting extension data for this invoke. Extension data is a map, keyed by an integer
   extension id, the value can be any serializable data structure. Extension map will be
   sent to remote server using same content type with the request body."
  [ext-map & body]
  `(binding [*extensions* ~ext-map] ~@body))

(def slacker-version
  (ver/get-version "slacker" "slacker"))
