(ns slacker.interceptors.slowwatchdog
  (:use [slacker.interceptor])
  (:require [clojure.tools.logging :as log]))

(definterceptor+
  ^{:doc "set threshold in ms the interceptor will log slow
          function calls"}
  slow-watch-dog
  [threshold]
  :before (fn [req]
            (assoc req :start-time (System/currentTimeMillis)))
  :after (fn [req]
           (let [now (System/currentTimeMillis)
                 cost (- now (:start-time req))]
             (when (> cost threshold)
               (log/warn (format "Slow call %s %s %dmsecs."
                                 (:fname req)
                                 (pr-str (:args req))
                                 cost))))
           req))

