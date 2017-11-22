(ns ^:no-doc slacker.protocol
  (:refer-clojure :exclude [byte float double])
  (:use [link.codec]))

(def ^:const v5 5)
(def ^:const v6 6)

(def versions
  (enum (byte) {v5 5
                v6 6}))

(def packet-type
  (enum (byte) {:type-request 0
                :type-response 1
                :type-ping 2
                :type-pong 3
                :type-error 4
                :type-auth-req 5
                :type-auth-ack 6
                :type-inspect-req 7
                :type-inspect-ack 8
                :type-interrupt 9
                :type-client-meta 10
                :type-client-meta-ack 11}))

(def content-type
  (enum (byte) {:carb 0 :json 1 :clj 2 :nippy 3
                :deflate-carb 10
                :deflate-json 11
                :deflate-clj 12
                :deflate-nippy 13}))

(def result-codes
  (enum (byte) {:success 0
                :not-found 11
                :exception 12
                :thread-pool-full 13
                :protocol-mismatch 20
                :invalid-packet 21
                :acl-reject 22}))

(def slacker-call-extension
  (frame
   (int16) ;; extension id
   (byte-block :prefix (uint16))))

;; :type-request
(def slacker-request-codec-v5
  (frame
   content-type
   (string :encoding :utf-8 :prefix (uint16))
   (byte-block :prefix (uint32))
   (const [])))

;; :type-response
(def slacker-response-codec-v5
  (frame
   content-type
   result-codes
   (byte-block :prefix (uint32))
   (const [])))

;; :type-request
(def slacker-request-codec-v6
  (frame
   content-type
   (string :encoding :utf-8 :prefix (uint16))
   (byte-block :prefix (uint32))
   (counted :prefix (byte) :body slacker-call-extension)))

;; :type-response
(def slacker-response-codec-v6
  (frame
   content-type
   result-codes
   (byte-block :prefix (uint32))
   (counted :prefix (byte) :body slacker-call-extension)))

;; :type-ping
(def slacker-ping-codec
  (frame))

;; :type-pong
(def slacker-pong-codec
  (frame))

;; :type-error
(def slacker-error-codec
  (frame
   result-codes))

;; :type-auth-req
(def slacker-auth-req-codec
  (frame
   (string :encoding :ascii :prefix (uint16))))

;; type-auth-ack
(def slacker-auth-ack-codec
  (frame
   (enum (byte) {:auth-ok 0
                 :auth-reject 1})))

;; type-inspect-req
(def slacker-inspect-req-codec
  (frame
   (enum (byte) {:functions 0
                 :meta 1})
   (byte-block :prefix (uint16))))

;; type-inspect-ack
(def slacker-inspect-ack-codec
  (frame
   (byte-block :prefix (uint16))))

;; type-interrupt
(def slacker-interrupt-codec
  (frame
   (int32)))

(def slacker-client-meta-codec
  (frame (byte-block :prefix (uint16))))

(def slacker-client-meta-ack-codec
  (frame
   (string :prefix (int32) :encoding :ascii)
   (string :prefix (int32) :encoding :ascii)))

(def slacker-v5-codec
  (frame
   (int32) ;; transaction id
   (header
    packet-type
    {:type-request slacker-request-codec-v5
     :type-response slacker-response-codec-v5
     :type-ping slacker-ping-codec
     :type-pong slacker-pong-codec
     :type-error slacker-error-codec
     :type-auth-req slacker-auth-req-codec
     :type-auth-ack slacker-auth-ack-codec
     :type-inspect-req slacker-inspect-req-codec
     :type-inspect-ack slacker-inspect-ack-codec
     :type-interrupt slacker-interrupt-codec
     :type-client-meta slacker-client-meta-codec
     :type-client-meta-ack slacker-client-meta-ack-codec})))

(def slacker-v6-codec
  (frame
   (int32)
   (header
    packet-type
    {:type-request slacker-request-codec-v6
     :type-response slacker-response-codec-v6
     :type-ping slacker-ping-codec
     :type-pong slacker-pong-codec
     :type-error slacker-error-codec
     :type-auth-req slacker-auth-req-codec
     :type-auth-ack slacker-auth-ack-codec
     :type-inspect-req slacker-inspect-req-codec
     :type-inspect-ack slacker-inspect-ack-codec
     :type-interrupt slacker-interrupt-codec
     :type-client-meta slacker-client-meta-codec
     :type-client-meta-ack slacker-client-meta-ack-codec})))

(def slacker-root-codec
  (header
    versions
    {v5 slacker-v5-codec
     v6 slacker-v6-codec}))

;; helper function

(defn of [v data]
  [(or v v6) data])
