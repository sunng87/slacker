(ns slacker.common)

(def
  ^{:doc "Debug flag. This flag can be override by binding if you like to see some debug output."}
  *debug* false)
(def
  ^{:doc "Timeout for synchronouse call."}
  *timeout* 10000)
(def
  ^{:doc "Protocol version."}
  version (short 2))


