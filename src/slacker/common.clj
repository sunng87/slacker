(ns slacker.common)

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
