(ns slacker.protocol-test
  (:require [clojure.test :refer :all]
            [slacker.protocol :refer :all]
            [link.codec :as codec])
  (:import [io.netty.buffer Unpooled]))

(deftest test-slacker-codec
  (are [data]
      (let [buf (Unpooled/buffer)]
        (= data (codec/decode* slacker-root-codec
                               (codec/encode* slacker-root-codec data buf))))

    [v5 [10 [:type-ping []]]]))
