(ns slacker.test.server.http
  (:use clojure.test)
  (:use [slacker.protocol :only [v6]] )
  (:use slacker.server.http)
  (:use slacker.serialization)
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]))

(defn make-fake-data-stream [string-data]
  (io/input-stream (.getBytes ^String string-data "UTF-8")))

(defn byteins->string [bis]
  (let [bos (ByteArrayOutputStream.)]
    (io/copy bis bos)
    (String. (.toByteArray bos) "UTF-8")))

(deftest test-http-request
  (let [in-data "[27 89]"
        req {:uri "/echo.clj" :body (make-fake-data-stream in-data)}
        sreq (ring-req->slacker-req req)]
    (is (= v6 (first sreq)))
    (is (= :type-request (let [[_ [_ [t]]] sreq] t)))
    (is (= :clj (let [[_ [_ [_ [ct]]]] sreq] ct)))
    (is (= "echo" (let [[_ [_ [_ [_ v]]]] sreq] v)))))

(deftest test-http-response
  (let [result (serialize :clj [1])
        sresp [v6 [0 [:type-response [:clj :success result []]]]]
        resp (slacker-resp->ring-resp sresp)]
    (is (= 200 (:status resp)))
    (is (= "application/clj" (-> resp :headers (get "content-type"))))))
