(ns slacker.test.server
  (:require [slacker.server :refer :all]
            [slacker.serialization :refer :all]
            [slacker.common :refer :all]
            [slacker.protocol :as protocol]
            [clojure.test :refer :all]
            [clojure.string :refer [split]]))

(def funcs {"plus" + "minus" - "prod" * "div" /})
(def params (serialize :carb [100 0]))

(deftest test-server-pipeline
  (let [server-pipeline (build-server-pipeline
                         funcs {:pre identity :before identity :after identity :post identity}
                         (atom {}))
        req {:content-type :carb
             :data params
             :fname "plus"}
        req2 {:content-type :carb
              :data params
              :fname "never-found"}
        req3 {:content-type :carb
              :data params
              :fname "div"}]

    (.rewind params)
    (let [result (server-pipeline req)]
      (is (= :success (:code result)))
      (is (= 100 (deserialize :carb (:result result)))))

    (.rewind params)
    (let [result (server-pipeline req2)]
      (is (= :not-found (:code result))))

    (.rewind params)
    (let [result (server-pipeline req3)]
      (is (= :exception (:code result))))))

(def interceptor (fn [req] (update-in req [:result] str)))

(deftest test-server-pipeline-interceptors
  (let [server-pipeline (build-server-pipeline
                         funcs {:pre identity :before identity :after interceptor :post identity}
                         (atom {}))
        req {:content-type :carb
             :data params
             :fname "prod"}]
    (.rewind params)
    (is (= "0" (deserialize :carb (:result (server-pipeline req)))))))

(deftest test-ping
  (let [request [protocol/v5 [0 [:type-ping]]]
        [_ [_ response]] (handle-request nil request nil nil nil nil)]
    (is (= :type-pong (nth response 0)))))

(deftest test-invalid-packet
  (let [request [protocol/v5 [0 [:type-unknown]]]
        [_ [_ response]] (handle-request nil request nil nil nil nil)]
    (is (= :type-error (nth response 0)))
    (is (= :invalid-packet (-> response
                               second
                               first)))))


(deftest test-functions-inspect
  (let [request [protocol/v5 [0 [:type-inspect-req [:functions "nil"]]]]
        [_ [_ [_ [result]]]] (handle-request nil request nil
                                             (build-inspect-handler funcs) nil nil)
        response (deserialize :clj result :string)]
    (= (map name (keys funcs)) response)))

(deftest test-parse-functions
  (testing "parsing function map"
    (let [nmap {"example.api2" {"echo2" (constantly "echo2")
                                "echo3" (constantly "echo3")
                                "echo4" (constantly "echo4")}
                "example.api3" {"reverse2" (constantly (reverse "reverse2"))
                                "reverse3" (constantly (reverse "reverse3"))}}
          parsed-results (parse-funcs nmap)]

      (is (= 5 (count parsed-results)))
      (is (= 3 (count (filter #(clojure.string/starts-with? % "example.api2/")
                              (keys parsed-results)))))
      (is (= 2 (count (filter #(clojure.string/starts-with? % "example.api3/")
                              (keys parsed-results)))))
      (is (= "echo2" ((parsed-results "example.api2/echo2"))))
      (is (= (reverse "reverse2") ((parsed-results "example.api3/reverse2")))))))
