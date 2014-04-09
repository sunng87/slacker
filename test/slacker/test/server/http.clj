(ns slacker.test.server.http
  (:use clojure.test)
  (:use [slacker.protocol :only [version]] )
  (:use slacker.server.http)
  (:use slacker.serialization)
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]))

(defn make-fake-data-stream [string-data]
  (io/input-stream (.getBytes string-data "UTF-8")))

(defn byteins->string [bis]
  (let [bos (ByteArrayOutputStream.)]
    (io/copy bis bos)
    (String. (.toByteArray bos) "UTF-8")))

(deftest test-http-request
  (let [in-data "[27 89]"
        req {:uri "/echo.clj" :body (make-fake-data-stream in-data)}
        sreq (ring-req->slacker-req req)]
    (is (= version (first sreq)))
    (is (= :type-request (-> sreq (nth 2) first)))
    (is (= :clj (-> sreq (nth 2) second first)))
    (is (= "echo" (-> sreq (nth 2) second second)))))

(deftest test-http-response
  (let [result (serialize :clj [1])
        sresp [version 0 [:type-response [:clj :success result]]]
        resp (slacker-resp->ring-resp sresp)]
    (is (= 200 (:status resp)))
    (is (= "application/clj" (-> resp :headers (get "content-type"))))))
