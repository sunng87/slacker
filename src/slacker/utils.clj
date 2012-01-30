(ns slacker.utils
  (:refer-clojure :exclude [replace])
  (:use [clojure.string :only [join replace]]))


(defn zk-path
  "concat a list of string to zookeeper path"
  [& nodes]
  (str "/slacker/cluster/" (join "/" nodes)))

(defn escape-zkpath [fname]
  (replace fname "/" "_slash_"))

(defn unescape-zkpath [fname]
  (replace fname "_slash_" "/"))

