(ns slacker.test.client
  (:require [clojure.test :refer :all]
            [slacker.client.common :refer :all]))

(deftest test-slacker-factory
  (let [factory (create-client-factory nil)
        addr "127.0.0.1:5579"
        client (create-client factory addr :clj {:ping-interval 10})]
    (is (= 1 (count (get-states factory))))
    (is (= addr (-> factory get-states keys first)))
    (is (= client (-> factory (get-state addr) :refs first)))))

(deftest test-slacker-client
  (let [factory (create-client-factory nil)
        addr "127.0.0.1:5779"
        client (create-client factory addr :clj {:ping-interval 10})]
    (is (= addr (server-addr client)))
    (close client)
    (is (nil? (get-state factory addr)))
    (is (zero? (count (get-states factory))))))
