(ns slacker.test.serialization
  (:use slacker.serialization)
  (:use clojure.test)
  (:import [java.util Arrays]))

(deftest test-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :carb (serialize :carb data)))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :carb (serialize :carb data))))))

(deftest test-json-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :json (serialize :json data))))
    (is (= data (deserialize :json (serialize :json data :bytes) :bytes))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :json (serialize :json data))))))

(deftest test-clj-serialization
  (let [data [1 2 3]]
    (is (= data (deserialize :clj (serialize :clj data))))
    (is (= "[1 2 3]" (serialize :clj data :string)))
    (is (Arrays/equals (.getBytes "[1 2 3]" "UTF-8") (serialize :clj data :bytes))))
  (let [data {:a 1 :b 2}]
    (is (= data (deserialize :clj (serialize :clj data))))))


