(ns slacker.aclrules)

(declare authorize )
(defmacro defrules
  "Define an ACL rule. You can specify ACL rules for each server"
  [req  &clauses]
  `(if (authorize ~req) (-> ~req ~@clauses)
      assoc req :result "Access denied due to ACL reason!"))

