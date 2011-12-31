(ns slacker.utils
  (:use [slacker.client :only [defn-remote]]))

(defmacro defn-remote-batch [sc & fnames]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~f)) fnames)))

