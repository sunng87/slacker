(ns slacker.test.server.http
  (:use clojure.test)
  (:use slacker.server.http)
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]))

(defn fake-server-handler [req]
  (assoc req :code :success :result (:data req)))

(defn make-fake-data-stream [string-data]
  (io/input-stream (.getBytes string-data "UTF-8")))

(defn byteins->string [bis]
  (let [bos (ByteArrayOutputStream.)]
    (io/copy bis bos)
    (String. (.toByteArray bos) "UTF-8")))

(deftest test-http
  (let [handler (wrap-http-server-handler fake-server-handler)
        in-data "[27 89]"
        req {:uri "/echo.clj" :body (make-fake-data-stream in-data)}
        resp (handler req)]
    (is (= 200 (:status resp)))
    (is (= in-data (byteins->string (:body resp))))
    (is (= "application/clj" (get (:headers resp) "content-type")))))


