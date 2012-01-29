(ns slacker.server.http
  (:require [clojure.string :as string])
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream])
  (:import [java.nio ByteBuffer])
  (:import [java.nio.charset Charset]))

(defn- instream-to-bb [inputstream]
  (let [out (ByteArrayOutputStream.)]
    (io/copy inputstream out)
    (ByteBuffer/wrap (.toByteArray out))))

(defn- bb-to-string [bb]
  (.toString (.decode (Charset/forName "UTF-8") bb)))

(defn- decode-http-data [req]
  (let [{uri :uri body :body} req
        content-type (last (string/split uri #"\."))
        fname (.substring uri 1 (dec (.lastIndexOf uri content-type)))
        content-type (keyword content-type)
        body (or body "[]")
        data [(instream-to-bb body)]] ;; gloss finite-block workaround
    {:packet-type :type-request
     :content-type content-type
     :fname fname
     :data data}))

(defn- encode-http-data [req]
  (let [{ct :content-type code :code result :result} req
        content-type (str "application/" (name ct))
        status (case (:code req)
                 :success 200
                 :exception 500
                 :not-found 404
                 400)
        body (and result (bb-to-string result))]
    {:status status
     :headers {"content-type" content-type}
     :body (str body "\r\n")}))

(defn wrap-http-server-handler
  "wrap a standard server-pipeline to support ring style
  handler."
  [server-handler]
  (fn [req]
    (-> req
        decode-http-data
        server-handler
        encode-http-data)))


