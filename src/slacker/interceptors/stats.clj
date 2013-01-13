(ns slacker.interceptors.stats
  (:use [slacker.interceptor])
  (:require [clojure.java.jmx :as jmx])
  (:import [javax.management DynamicMBean MBeanInfo
            MBeanAttributeInfo Attribute AttributeList
            MBeanOperationInfo]))

(def stats-data (agent {} :error-mode :continue))

(defn assoc-with-default [m k]
  (update-in m [k] #(if (nil? %) 1 (inc %))))

(defn reset-stats []
  (send stats-data (constantly {}))
  nil)

(definterceptor
  ^{:doc "an interceptor that counts function-call"}
  function-call-stats
  :before (fn [req]
            (when (nil? (:code req))
              (let [fname (:fname req)]
                (send stats-data assoc-with-default fname)))
            req))

(defmulti jmx-invoke (fn [a _ _ ] a))
(defmethod jmx-invoke "reset" [action params sig]
  (reset-stats))

(def ^{:private true} stats-bean
  (reify
    DynamicMBean
    (getAttribute [this key]
      (@stats-data key))
    (getAttributes [this keys]
      (AttributeList.
       (apply list (map #(Attribute. % (get @stats-data %)) keys))))
    (getMBeanInfo [this]
      (MBeanInfo. (.. @stats-data getClass getName)
                  "slacker function call statistics"
                  (into-array
                   MBeanAttributeInfo
                   (map #(MBeanAttributeInfo. %
                                              "java.lang.Integer"
                                              "function invocation count"
                                              true
                                              false
                                              false)
                        (keys @stats-data)))
                  nil
                  (into-array
                   MBeanOperationInfo
                   [(MBeanOperationInfo. "reset"
                                         "reset all counters"
                                         nil
                                         "void"
                                         MBeanOperationInfo/ACTION)])
                  nil))
    (invoke [this action params sig]
      (jmx-invoke action params sig))
    (setAttribute [this attr])
    (setAttributes [this attrs])))

(jmx/register-mbean stats-bean "slacker.server:type=FunctionCallStats")

