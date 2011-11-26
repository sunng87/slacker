(ns slacker.test.common
  (:use slacker.common)
  (:use clojure.test))

(deftest test-serialization
  (let [data [1 2 3]]
    (is (= data (read-carb (write-carb data))))))

