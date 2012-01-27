(ns slacker.utils
  (:refer-clojure :exclude [replace])
  (:use [slacker common serialization])
  (:use [slacker.client.common :only [inspect]])
  (:use [clojure.string :only [split join replace]]))

(defn get-all-funcs
  "inspect server to get all exposed function names."
  ([sc] (get-all-funcs sc nil))
  ([sc n] (inspect sc :functions n)))


(defn defn-remote-all
  "defn-remote automatically by inspect server"
  ([sc-sym]
     (dorun (map #(eval (list 'defn-remote sc-sym
                              (symbol (second (split % #"/")))
                              :remote-ns (first (split % #"/"))))
                 (get-all-funcs @(find-var sc-sym))))))


(defn zk-path
  "concat a list of string to zookeeper path"
  [& nodes]
  (str "/slacker/cluster/" (join "/" nodes)))

(defn escape-zkpath [fname]
  (replace fname "/" "_slash_"))

(defn unescape-zkpath [fname]
  (replace fname "_slash_" "/"))

