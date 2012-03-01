(ns slacker.test.aclmodule.aclrules
  (:use [clojure.test])
  (:use [slacker.aclrules])
  (:use slacker.aclmodule.authorize)
  (:import java.net.InetSocketAddress))

(def client-info {:remote-addr (InetSocketAddress. "192.168.1.1" 9090)})
(def other-client {:remote-addr (InetSocketAddress. "192.168.15.1" 9090)})
(def another-client {:remote-addr (InetSocketAddress. "192.168.1.10" 9090)})
(defrules myrules
      (deny ["192.168.1.10"])
      (allow ["192.168.1.*" "192.168.100.*"]))

 (defrules lightrules
      (deny :all)
      (allow :all))
(defrules darkrules
      (deny ["192.168.1.10"])
      (allow ["192.168.1.10"]))

(defrules emptyrules
      (deny ["192.168.1.10"]))

(deftest test-defrules
    (is (= myrules {:deny ["192.168.1.10"]
                    :allow ["192.168.1.*" "192.168.100.*"]}))
    (is (= lightrules {:deny :all :allow :all}))
    (is (= darkrules {:deny ["192.168.1.10"] :allow ["192.168.1.10"]})))

(deftest test-authorize
  (is (true?  (authorize client-info myrules) ))
  (is (false? (authorize other-client myrules)) )
  (is (false? (authorize another-client myrules)) )
  (is (true?  (authorize client-info lightrules)) )
  (is (true?  (authorize other-client lightrules)) )
  (is (true?  (authorize another-client darkrules)) )
  (is (true?  (authorize another-client {})) )
  (is (false? (authorize client-info emptyrules)))
  )
