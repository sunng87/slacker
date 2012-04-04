(ns slacker.interceptors.logargs
  (:use [slacker.interceptor])
  (:require [clojure.tools.logging :as log]))

(definterceptor+
  ^{:doc "log arguments when of calls that cause exception.
          To use this interceptor, you are suggested to put log4j
          and its configuration in your classpath."}
  logargs
  [level]
  :after (fn [req]
           (when (= (:code req) :exception)
             (log/log level (format "Exception call %s %s %s"
                                    (:fname req)
                                    (pr-str (:args req))
                                    (:result req))))
           req))

