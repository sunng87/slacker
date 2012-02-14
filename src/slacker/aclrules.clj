(ns slacker.aclrules)

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
