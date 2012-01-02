(ns slacker.test.server
  (:use [slacker server serialization common])
  (:use [clojure.test])
  (:use [clojure.string :only [split]]))

(def funcs {"plus" + "minus" - "prod" * "div" /})
(def params (serialize :carb [100 0]))

(deftest test-server-pipeline
  (let [server-pipeline (build-server-pipeline funcs {:before identity :after identity})
        req {:content-type :carb
             :data [params]
             :fname "plus"}
        req2 {:content-type :carb
              :data [params]
              :fname "never-found"}
        req3 {:content-type :carb
              :data [params]
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
  (let [server-pipeline (build-server-pipeline funcs {:before identity :after interceptor})
        req {:content-type :carb
             :data [params]
             :fname "prod"}]
    (.rewind params)
    (is (= "0" (deserialize :carb (:result (server-pipeline req)))))))

(deftest test-ping
  (let [request [version [:type-ping :json nil nil]]
        response (second (handle-request nil request nil nil))]
    (is (= :type-pong (nth response 0)))))

(deftest test-invalid-packet
  (let [request [version [:type-unknown :json nil nil]]
        response (second (handle-request nil request nil nil))]
    (is (= :type-error (nth response 0)))
    (is (= :invalid-packet (nth response 1)))))


(deftest test-functions-inspect
  (let [request [version [:type-inspect-req :functions]]
        response (deserialize :clj (second (second (handle-request nil request nil (build-inspect-handler true funcs)))))]
    (= (map name (keys funcs)) response)))

