(ns slacker.test.common
  (:use slacker.common)
  (:use clojure.test))

(deftest test-serialization
  (let [data [1 2 3]]
    (is (= data (read-carb (write-carb data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (read-carb (write-carb data))))))

(deftest test-json-serialization
  (let [data [1 2 3]]
    (is (= data (read-json (write-json data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (read-json (write-json data))))))

