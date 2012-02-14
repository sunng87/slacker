(ns slacker.test.aclrules
  (:use clojure.test)
  (:use midje.sweet)
  (:use slacker.aclrules)
  (:use slacker.aclmodule.authorize))


(fact (-> {} (allow ["ip-list"]))
      =>
      {:allow ["ip-list"]})

(fact (defrules myrules
        (deny ["192.168.1.10"])
        (allow ["192.168.1.*" "192.168.100.*"]))
      myrules
      =>
      {:deny ["192.168.1.10"]
       :allow ["192.168.1.*" "192.168.100.*"]})

(fact (defrules lightrules
        (deny :all)
        (allow :all))
      lightrules
      =>
      {:deny :all
       :allow :all})

(fact (defrules darkrules
        (deny ["192.168.1.10"])
        (allow ["192.168.1.10"]))
      darkrules
      =>
      {:deny ["192.168.1.10"]
       :allow ["192.168.1.10"]})

(def client-info "192.168.1.1")
(def other-client "192.168.15.1")
(def another-client "192.168.1.10")

(fact (authorize client-info myrules)
      => true)

(fact (authorize other-client myrules)
      => false)

(fact (authorize another-client myrules)
      => false)

(fact (authorize client-info lightrules)
      => true)

(fact (authorize other-client lightrules)
      => true)

(fact (authorize another-client darkrules)
      => true)
