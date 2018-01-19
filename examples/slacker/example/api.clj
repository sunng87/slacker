(ns slacker.example.api
  (:require [slacker.common :as s]
            [manifold.deferred :as d]
            [clojure.tools.logging :as logging]))

(defn timestamp []
  (System/currentTimeMillis))

(def ^{:private true} m (atom 0))
(defn inc-m [amount]
  (swap! m + amount))

(defn get-m []
  (logging/info "Getting client extensions" s/*extensions*)
  @m)

(defn rand-ints
  "test collection result"
  [n]
  (map (fn [_] (rand-int Integer/MAX_VALUE)) (range n)))

(defn make-error
  "test runtime exception"
  []
  (throw (RuntimeException. "Expected exception.")))

(defn echo
  ([x] x)
  ([x y] [x y]))

(defn return-nil []
  nil)

(defn return-zero []
  0)

(defn long-running []
  (Thread/sleep 100000)
  true)

(defn ^:no-slacker unreachable [] true)

(defn -slacker-function-not-found [fname args]
  ["Function not found: " fname args])

(defn async-result []
  (let [defr (d/deferred)]
    (doto (Thread. #(do
                      (Thread/sleep 300)
                      (d/success! defr "Deferred result.")))
      (.start))
    defr))

(defn async-exception []
  (let [defr (d/deferred)]
    (doto (Thread. #(do
                      (d/error! defr (RuntimeException. "Expected error in async fn."))))
      (.start))
    defr))
