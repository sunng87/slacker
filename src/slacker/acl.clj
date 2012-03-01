(ns slacker.acl)

(defmacro allow [rules ip-list]
  `(assoc ~rules :allow ~ip-list))


(defmacro deny [rules ip-list]
  `(assoc ~rules :deny ~ip-list))


(defmacro defrules
  "create a set of acl rules"
  [rules & clauses]
  `(def ~rules
     (-> {} ~@clauses)))
