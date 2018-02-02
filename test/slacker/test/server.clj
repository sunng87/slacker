(ns slacker.test.server
  (:require [slacker.server :refer :all]
            [slacker.serialization :refer :all]
            [slacker.common :refer :all]
            [slacker.protocol :as protocol]
            [clojure.test :refer :all]
            [clojure.string :refer [split]]
            [link.core :as link]
            [link.mock :as mock]
            [manifold.deferred :as d])
  (:import [io.netty.buffer Unpooled]))

(def funcs {"a/plus" + "a/minus" - "a/prod" * "a/div" /
            "a/async" (fn [] (let [defr (d/deferred)]
                              (d/success! defr 1)
                              defr))})

(deftest test-request-handler
  (let [server-pipeline (build-server-pipeline {} (atom {}))
        req1 [protocol/v6
              [1 [:type-request
                  [:nippy "a/plus" (serialize :nippy [100 0]) {}]]]]
        ch1 (mock/mock-channel {})

        req2 [protocol/v6
              [2 [:type-request
                  [:nippy "a/div" (serialize :nippy [100 0]) {}]]]]
        ch2 (mock/mock-channel {})

        req3 [protocol/v6
              [3 [:type-request
                  [:clj "a/async" (serialize :clj []) {}]]]]
        ch3 (mock/mock-channel {})]

    (handle-request {:funcs funcs
                     :server-pipeline server-pipeline}
                    req1 {:channel ch1})
    (handle-request {:funcs funcs
                     :server-pipeline server-pipeline}
                    req2 {:channel ch2})
    (handle-request {:funcs funcs
                     :server-pipeline server-pipeline}
                    req3 {:channel ch3})

    (let [[_ [_ [_ [ct code result _]]]] (first @ch1)]
      (is (= :success code))
      (is (= 100 (deserialize :nippy result))))

    (let [[_ [_ [_ [ct code result _]]]] (first @ch2)]
      (is (= :exception code)))

    (let [[_ [_ [_ [ct code result _]]]] (first @ch3)]
      (is (= :success code))
      (is (= 1 (deserialize :clj result))))))

(def interceptor (fn [req] (update-in req [:result] str)))

(deftest test-server-pipeline-interceptors
  (let [server-pipeline (build-server-pipeline
                         {:pre identity :before identity :after interceptor :post identity}
                         (atom {}))
        req {:content-type :nippy
             :data (serialize :nippy [100 0])
             :fname "a/prod" :func *}]

    (is (= "0" (deserialize :nippy (:result (server-pipeline req)))))))

(deftest test-ping
  (let [ch (mock/mock-channel {})
        request [protocol/v5 [0 [:type-ping]]]]
    (handle-request {} request {:channel ch})
    (let [[_ [_ response]] (first @ch)]
      (is (= :type-pong (first response))))))

(deftest test-invalid-packet
  (let [ch (mock/mock-channel {})
        request [protocol/v5 [0 [:type-unknown]]]]
    (handle-request {} request {:channel ch})
    (let [[_ [_ response]] (first @ch)]
      (is (= :type-error (nth response 0)))
      (is (= :invalid-packet (-> response
                                 second
                                 first))))))

(deftest test-functions-inspect
  (let [ch (mock/mock-channel {})
        request [protocol/v5 [0 [:type-inspect-req [:functions
                                                    (Unpooled/wrappedBuffer (.getBytes "\"a\""))]]]]]
    (handle-request {:inspect-handler (build-inspect-handler funcs (atom {}))}
                    request {:channel ch})
    (let [[_ [_ [_ [result]]]] (first @ch)
          response (deserialize :clj result)]
      (is (= (keys funcs) response)))))

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
