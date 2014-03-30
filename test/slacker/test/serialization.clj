(ns slacker.test.serialization
  (:use slacker.serialization)
  (:use clojure.test)
  (:import [java.util Arrays]))

(deftest test-serialization
  (are [data] (is (= data (deserialize :carb
                                       (serialize :carb data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742))

(deftest test-json-serialization
  (are [data] (is (= data (deserialize :json
                                       (serialize :json data))))
       [1 2 3]
       {:a 1 :b 2}
       "hello"
       87742)
  (are [data] (is (= data (deserialize :json
                                       (serialize :json data :bytes)
                                       :bytes)))
       [1 2 3]
       {:a 1 :b 2}
       "hello"
       87742)
  (are [data] (is (= data (deserialize :json
                                       (serialize :json data :string)
                                       :string)))
       [1 2 3]
       {:a 1 :b 2}
       "hello"
       87742)
  (is (= "[1,2,3]" (serialize :json [1 2 3] :string))))

(deftest test-clj-serialization
  (are [data] (is (= data (deserialize :clj
                                       (serialize :clj data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742)
  (are [data] (is (= data (deserialize :clj
                                       (serialize :clj data :bytes)
                                       :bytes)))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742)
  (are [data] (is (= data (deserialize :clj
                                       (serialize :clj data :string)
                                       :string)))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742)
  (is (= "[1 2 3]" (serialize :clj [1 2 3] :string))))

(deftest test-nippy-serialization
  (are [data] (is (= data (deserialize :nippy
                                       (serialize :nippy data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742)
  (are [data] (is (= data (deserialize :nippy
                                       (serialize :nippy data :bytes)
                                       :bytes)))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742))

(deftest test-compression
  (are [data] (is (= data (deserialize :deflate-carb
                                       (serialize :deflate-carb data))))
       [1 2 3]
       {:a 1 :b 2}
       #{1 2 3}
       "hello"
       :world
       87742))
