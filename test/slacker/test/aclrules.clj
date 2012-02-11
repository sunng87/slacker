(ns slacker.test.aclrules
  (:use clojure.test)
  (:use midje.sweet)
  (:use slacker.aclrules))


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
