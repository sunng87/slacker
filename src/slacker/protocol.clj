(ns slacker.protocol
  (:use [gloss.core]))

(defcodec packet-type
  (enum :byte {:type-request 0
               :type-response 1
               :type-ping 2
               :type-pong 3
               :type-error 4}))

(defcodec content-type
  (enum :byte {:carb 0 :json 1}))

(defcodec result-codes
  (enum :byte {:success 0
               :not-found 11
               :exception 12
               :protocol-mismatch 13
               :invalid-packet 14}))

(defcodec slacker-request-codec
  [:byte ;; protocol version
   packet-type ;; packet-type
   content-type ;; content-type
   (finite-frame :int16 (string :utf8)) ;; function name
   (finite-block :int16) ;; arguments
   ])

(defcodec slacker-response-codec
  [:byte ;; protocol version
   packet-type ;; packet-type
   content-type ;; content-type
   result-codes ;; result code
   (finite-block :int16) ;; result data
   ])


