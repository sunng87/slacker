(ns slacker.test.aclrules
  (:use clojure.test)
  (:use midje.sweet)
  (:use slacker.aclrules)
  (:use slacker.aclmodule.authorize)
  (:import java.net.InetSocketAddress))


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

(def client-info {:remote-addr (InetSocketAddress. "192.168.1.1" 9090)})
(def other-client {:remote-addr (InetSocketAddress. "192.168.15.1" 9090)})
(def another-client {:remote-addr (InetSocketAddress. "192.168.1.10" 9090)})

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

(fact (authorize another-client {})
      => true)
