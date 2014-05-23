(ns slacker.test.client
  (:require [clojure.test :refer :all]
            [slacker.client.common :refer :all])
  (:import [java.net ServerSocket]))

(defn- open-server [port p]
  (let [s (ServerSocket. port)]
    (.accept s)
    @p
    (.close s)))

(deftest test-slacker-factory
  (let [sync (promise)
        server (future (open-server 5579 sync))
        factory (create-client-factory nil)
        addr "127.0.0.1:5579"
        client (create-client factory addr :clj {:ping-interval 10})]
    (is (= 1 (count (get-states factory))))
    (is (= addr (-> factory get-states keys first)))
    (is (= client (-> factory (get-state addr) :refs first)))
    (deliver sync nil)))

(deftest test-slacker-client
  (let [sync (promise)
        server (future (open-server 5779 sync))
        factory (create-client-factory nil)
        addr "127.0.0.1:5779"
        client (create-client factory addr :clj {:ping-interval 10})]
    (is (= addr (server-addr client)))
    (close client)
    (is (nil? (get-state factory addr)))
    (is (zero? (count (get-states factory))))
    (deliver sync nil)))
