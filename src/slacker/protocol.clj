(ns slacker.protocol
  (:use [gloss.core]))

(defcodec packet-type
  (enum :byte {:type-request 0
               :type-response 1
               :type-ping 2
               :type-pong 3
               :type-error 4
               :type-auth-req 5
               :type-auth-ack 6}))

(defcodec content-type
  (enum :byte {:carb 0 :json 1 :clj 2}))

(defcodec result-codes
  (enum :byte {:success 0
               :not-found 11
               :exception 12
               :protocol-mismatch 20
               :invalid-packet 21}))

(defcodec slacker-request-codec
  [:type-request ;; packet-type
   content-type ;; content-type
   (finite-frame :uint16 (string :utf8)) ;; function name
   (finite-block :uint32) ;; arguments
   ])

(defcodec slacker-response-codec
  [:type-response ;; packet-type
   content-type ;; content-type
   result-codes ;; result code
   (finite-block :uint32) ;; result data
   ])

(defcodec slacker-ping-codec
  [:type-ping])

(defcodec slacker-pong-codec
  [:type-pong])

(defcodec slacker-error-codec
  [:type-error
   result-codes])

(defcodec slacker-auth-req-codec
  [:type-auth-req
   (finite-frame :uint16 (string :ascii))])

(defcodec slacker-auth-ack-codec
  [:type-auth-ack
   (enum :byte {:auth-ok 0
                :auth-reject 1})])

(defcodec slacker-base-codec
  [:byte ;; protocol version
   (header
    packet-type
    {:type-request slacker-request-codec
     :type-response slacker-response-codec
     :type-ping slacker-ping-codec
     :type-pong slacker-pong-codec
     :type-error slacker-error-codec ;; reuse response packet here
     :type-auth-req slacker-auth-req-codec
     :type-auth-ack slacker-auth-ack-codec}
    first)])

