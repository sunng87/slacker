(ns slacker.aclrules
  (:use clojure.test)
  (:use midje.sweet)
 )

(defmacro allow [rules ip-list]
  `(assoc ~rules :allow ~ip-list))


(defmacro deny [rules ip-list]
  `(assoc ~rules :deny ~ip-list))


(defmacro defrules
  "mapping parameters into a map"
  [rules & clauses]
  `(def ~rules
    (-> {} ~@clauses))
  )



(def myrules)

(fact (-> {} (allow ["ip-list"]))
      =>
      {:allow ["ip-list"]})


(fact (defrules myrules
        (deny :all)
        (allow ["192.168.10.*" "192.168.100.*"]))
      myrules
      =>
      {:deny :all
       :allow ["192.168.10.*" "192.168.100.*"]})


