(ns slacker.utils
  (:use [slacker common serialization])
  (:use [slacker.client :only [defn-remote]])
  (:use [slacker.client.common :only [inspect]])
  (:require [clojure.string :as string]))

(defmacro defn-remote-batch
  "a helper macro for defn-remote, allow multiple function names"
  [sc & fnames]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~f)) fnames)))

(defn get-all-funcs
  "inspect server to get all exposed function names."
  [sc]
  (inspect sc :functions nil))


(defn defn-remote-all
  "defn-remote automatically by inspect server"
  [sc-sym]
  (dorun (map #(eval (list 'defn-remote sc-sym (symbol %)))
              (get-all-funcs @(find-var sc-sym)))))

(defn meta-remote
  "get metadata of a remote function by inspect api"
  [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (inspect sc :meta fname)))

(defn zk-path
  "concat a list of string to zookeeper path"
  [& nodes]
  (str "/slacker/cluster/" (string/join "/" nodes)))


