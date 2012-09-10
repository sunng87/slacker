(ns slacker.interceptors.exectime
  (:use [slacker.interceptor])
  (:require [clojure.java.jmx :as jmx])
  (:import [javax.management DynamicMBean MBeanInfo
            MBeanAttributeInfo Attribute AttributeList
            MBeanOperationInfo]))

(def time-data (agent {} :error-mode :continue))
(def ^{:private true :const true} bound 50)

(defn enqueue-time-data [m k v]
  (update-in m [k]
             #(if (nil? %)
                (list v)
                (if (< (count %) bound)
                  (conj % v)
                  (conj (drop-last %) v)))))

(definterceptor
  ^{:doc "an interceptor records last 50 execution time (success call)
          of each function. data exposed via jmx: ExecTimeStats. Remember
          to put this in the last of your interceptor chain."}
  exectime-stats
  :before (fn [req]
            (assoc req :start-time (System/currentTimeMillis)))
  :after (fn [req]
           (when (= (:code req) :success)
             (let [fname (:fname req)
                   call-time (- (System/currentTimeMillis)
                                (:start-time req))]
               (send time-data enqueue-time-data fname call-time)))
           req))

(def ^{:private true} stats-bean
  (reify
    DynamicMBean
    (getAttribute [this key]
      (let [data (@time-data key)]
        (double (/ (apply + data) (count data)))))
    (getAttributes [this keys]
      (AttributeList.
       (apply list (map #(Attribute. % (get @time-data %)) keys))))
    (getMBeanInfo [this]
      (MBeanInfo. (.. @time-data getClass getName)
                  "slacker function execution time statistics"
                  (into-array
                   MBeanAttributeInfo
                   (map #(MBeanAttributeInfo. %
                                              "java.lang.Long"
                                              "average execution time (ms)"
                                              true
                                              false
                                              false)
                        (keys @time-data)))
                  nil
                  nil
                  nil))
    (invoke [this action params sig])
    (setAttribute [this attr])
    (setAttributes [this attrs])))

(jmx/register-mbean stats-bean "slacker.server:type=ExecTimeStats")
  
