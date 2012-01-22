(ns slacker.utils
  (:use [slacker common serialization])
  (:use [slacker.client.common :only [inspect]])
  (:use [clojure.string :only [split join]]))

(defn get-all-funcs
  "inspect server to get all exposed function names."
  [sc]
  (inspect sc :functions nil))


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


