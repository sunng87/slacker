(ns slacker.utils
  (:use [slacker common serialization])
  (:use [slacker.client :only [defn-remote]])
  (:use [slacker.client.common :only [introspect]]))

(defmacro defn-remote-batch [sc & fnames]
  `(do ~@(map (fn [f] `(defn-remote ~sc ~f)) fnames)))

(defn get-all-funcs [sc]
  (introspect sc :functions nil))


(defn defn-remote-all [sc]
  (dorun (map #(eval (list 'defn-remote 'sc (symbol %)))
              (get-all-funcs sc))))

(defn meta-remote [sc f]
  (let [fname (if (fn? f)
                (name (:name (meta f)))
                (str f))]
    (introspect sc :meta fname)))


