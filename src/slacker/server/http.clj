(ns ^:no-doc slacker.server.http
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [slacker.protocol :as protocol])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [io.netty.buffer ByteBuf ByteBufOutputStream Unpooled]))

(defn- stream->bytebuffer [inputstream]
  (let [buf (Unpooled/buffer)
        out (ByteBufOutputStream. buf)]
    (io/copy inputstream out)
    buf))

(defn- bytebuffer->stream [^ByteBuf bb]
  (let [buf-size (.readableBytes bb)
        buf (byte-array buf-size)]
    (.readBytes bb buf)
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
    (protocol/of protocol/v6 [0 [:type-request [content-type fname data []]]])))

(defn slacker-resp->ring-resp
  "transform slacker response to ring response"
  [resp]
  (let [[_ [_ resp-body]] resp
        packet-type (first resp-body)]
    (let [[ct code result _] (second resp-body)
          content-type (str "application/" (name ct))
          status (case code
                   :success 200
                   :exception 500
                   :not-found 404
                   400)
          body (and result (bytebuffer->stream result))]
      {:status status
       :headers {"content-type" content-type}
       :body body})))



(defn http-client-info
  "Get http client information (remote IP) from ring request"
  [req]
  (select-keys req [:remote-addr]))
