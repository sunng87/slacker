(ns slacker.util
  (:import [java.net ConnectException InetSocketAddress InetAddress]))

(defn inet-hostport[^InetAddress addr]
  (str (.getHostAddress ^InetAddress
                        (.getAddress ^InetSocketAddress addr))
       ":" (.getPort ^InetSocketAddress addr)))
