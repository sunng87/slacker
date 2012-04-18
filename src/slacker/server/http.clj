(ns slacker.server.http
  (:require [clojure.string :as string])
  (:require [clojure.java.io :as io])
  (:require [slacker.common])
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

(defn ring-req->slacker-req [req]
  (let [{uri :uri body :body} req
        content-type (last (string/split uri #"\."))
        fname (.substring ^String uri
                          1 (dec (.lastIndexOf ^String uri
                                               ^String content-type)))
        content-type (keyword content-type)
        body (or body "[]")
        data (stream->bytebuffer body)] 
    {:version slacker.common/version
     :packet-type :type-request
     :content-type content-type
     :fname fname
     :data data}))

(defn slacker-resp->ring-resp [req]
  (let [{ct :content-type code :code result :result} req
        content-type (str "application/" (name ct))
        status (case (:code req)
                 :success 200
                 :exception 500
                 :not-found 404
                 400)
        body (and result (bytebuffer->stream result))]
    {:status status
     :headers {"content-type" content-type}
     :body body}))



