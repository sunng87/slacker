(ns slacker.server.http
  (:require [clojure.string :as string])
  (:require [clojure.java.io :as io])
  (:require [slacker.protocol])
  (:import [java.io
            ByteArrayInputStream
            ByteArrayOutputStream])
  (:import [java.nio ByteBuffer]))

(defn- stream->bytebuffer [inputstream]
  (let [out (ByteArrayOutputStream.)]
    (io/copy inputstream out)
    (ByteBuffer/wrap (.toByteArray out))))

(defn- bytebuffer->stream [^ByteBuffer bb]
  (let [buf-size (.remaining bb)
        buf (byte-array buf-size)]
    (.get bb buf)
    (ByteArrayInputStream. buf)))

(defn ring-req->slacker-req
  "transform ring request to slacker request"
  [req]
  (let [{uri :uri body :body} req
        content-type (last (string/split uri #"\."))
        fname (.substring ^String uri
                          1 (dec (.lastIndexOf ^String uri
                                               ^String content-type)))
        content-type (keyword content-type)
        body (or body "[]")
        data (stream->bytebuffer body)]
    [slacker.protocol/version 0 [:type-request [content-type fname data]]]))

(defn slacker-resp->ring-resp
  "transform slacker response to ring response"
  [resp]
  (let [resp-body (nth resp 2)
        packet-type (first resp-body)]
    (if (and (= :type-error packet-type)
             (= :acl-reject (-> resp-body second first)))
      ;; rejected by acl, return HTTP403
      {:status 403
       :body "rejected by access control list"}
      ;; normal response packet
      (let [[ct code result] (second resp-body)
            content-type (str "application/" (name ct))
            status (case code
                     :success 200
                     :exception 500
                     :not-found 404
                     400)
            body (and result (bytebuffer->stream result))]
        {:status status
         :headers {"content-type" content-type}
         :body body}))))



(defn http-client-info
  "Get http client information (remote IP) from ring request"
  [req]
  (select-keys req [:remote-addr]))
