(ns slacker.test.serialization
  (:use slacker.serialization)
  (:use clojure.test))

(deftest test-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :carb (serialize :carb data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :carb (serialize :carb data))))))

(deftest test-json-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :json (serialize :json data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :json (serialize :json data))))))

(deftest test-clj-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :clj (serialize :clj data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :clj (serialize :clj data))))))

