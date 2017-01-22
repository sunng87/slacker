(ns slacker.test.serialization
  (:use slacker.serialization)
  (:use clojure.test)
  (:import [java.util Arrays]
           [io.netty.buffer UnpooledByteBufAllocator]))

(deftest test-json-serialization
  (are [data] (is (= data (deserialize :json
                                       (serialize :json UnpooledByteBufAllocator/DEFAULT data))))
       [1 2 3]
       {:a 1 :b 2}
       "hello"
       87742))

(deftest test-clj-serialization
  (are [data] (is (= data (deserialize :clj
                                       (serialize :clj UnpooledByteBufAllocator/DEFAULT data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742))

(deftest test-nippy-serialization
  (are [data] (is (= data (deserialize :nippy
                                       (serialize :nippy UnpooledByteBufAllocator/DEFAULT data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742))
