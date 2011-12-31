(ns slacker.utils
  (:use [slacker.common])
  (:use [slacker.client :only [defn-remote]])
  (:use [slacker.client.common :only [introspect]])
  (:use [clojure.string :only [split]]))

(defmacro defn-remote-batch [sc & fnames]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~f)) fnames)))

(defn get-all-funcs [sc]
  (let [result (introspect sc :functions)]
    (split result #",")))

(defmacro defn-remote-all [sc]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~(symbol f))) `(get-all-funcs ~sc))))


