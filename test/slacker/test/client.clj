(ns slacker.test.client
  (:require [clojure.test :refer :all]
            [slacker.client.common :refer :all])
  (:import [java.net ServerSocket]))

(defn- open-server [port close-sync start-sync]
  (let [s (ServerSocket. port)]
    (deliver start-sync nil)
    (.accept s)
    @close-sync
    (.close s)))

(deftest test-slacker-factory
  (let [close-sync (promise)
        start-sync (promise)
        server (future (open-server 5579 close-sync start-sync))
        factory (create-client-factory nil)
        addr "127.0.0.1:5579"
        client (or @start-sync (create-client factory addr :clj {:ping-interval 10}))]
    (is (= 1 (count (get-states factory))))
    (is (= addr (-> factory get-states keys first)))
    (is (= client (-> factory (get-state addr) :refs first)))
    (close client)
    (deliver close-sync nil)))

(deftest test-slacker-client
  (let [close-sync (promise)
        start-sync (promise)
        server (future (open-server 5779 close-sync start-sync))
        factory (create-client-factory nil)
        addr "127.0.0.1:5779"
        client (or @start-sync (create-client factory addr :clj {:ping-interval 10}))]
    (is (= addr (server-addr client)))
    (close client)
    (is (nil? (get-state factory addr)))
    (is (zero? (count (get-states factory))))
    (deliver close-sync nil)))
