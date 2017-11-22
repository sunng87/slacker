(ns slacker.protocol-test
  (:require [clojure.test :refer :all]
            [slacker.protocol :refer :all]
            [slacker.serialization :refer [serialize deserialize]]
            [link.codec :as codec])
  (:import [io.netty.buffer Unpooled]))

(deftest test-slacker-codec
  (are [data]
      (let [buf (Unpooled/buffer)]
        (= data (codec/decode* slacker-root-codec
                               (codec/encode* slacker-root-codec data buf))))
    [v5 [10 [:type-ping []]]]
    [v6 [10 [:type-ping []]]]
    [v6 [10 [:type-client-meta-ack ["127.0.0.1:5533" "127.0.0.1:5533"]]]]
    [v6 [10 [:type-client-meta [(serialize :clj {:shit "123"})]]]]))
