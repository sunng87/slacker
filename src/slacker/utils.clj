(ns slacker.utils
  (:use [slacker common serialization])
  (:use [slacker.client :only [defn-remote]])
  (:use [slacker.client.common :only [inspect]]))

(defmacro defn-remote-batch [sc & fnames]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~f)) fnames)))

(defn get-all-funcs [sc]
  (inspect sc :functions nil))


(defn defn-remote-all [sc-sym]
  (dorun (map #(eval (list 'defn-remote sc-sym (symbol %)))
              (get-all-funcs @(find-var sc-sym)))))

(defn meta-remote [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (inspect sc :meta fname)))


