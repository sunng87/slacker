(ns slacker.example.api)

(defn timestamp []
  (System/currentTimeMillis))

(def ^{:private true} m (atom 0))
(defn inc-m [amount]
  (swap! m + amount))

(defn get-m [] @m)

