(ns slacker.example.api)

(defn timestamp []
  (System/currentTimeMillis))

(def ^{:private true} m (atom 0))
(defn inc-m [amount]
  (println "inc-m")
  (swap! m + amount))

(defn get-m []
  (println "get-m")
  @m)

