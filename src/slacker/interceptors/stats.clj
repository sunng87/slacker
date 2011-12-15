(ns slacker.interceptors.stats
  (:require [clojure.contrib.jmx :as jmx])
  (:import [clojure.contrib.jmx Bean]))

(def stats-data (atom {}))

(defn assoc-with-default [m k]
  (update-in m [k] #(if (nil? %) 1 (inc %))))

(defn function-call-stats [req]
  (when (nil? (:code req))
    (let [fname (:fname req)]
      (swap! stats-data assoc-with-default fname)))
  req)

(defn new-mbean [state-ref]
  (proxy [Bean] [state-ref]
    (getAttribute [attr] 
       (let [attr-value (@(.state ^clojure.contrib.jmx.Bean this) (keyword attr))]
         (if (fn? attr-value)
           (attr-value)
           attr-value)))))

(def stats-bean
  (new-mbean
   (ref {:stats (fn [] (hash-map @stats-data))})))

(jmx/register-mbean stats-bean "slacker.server:type=FunctionCallStats")

