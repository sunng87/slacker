(ns slacker.example.api)

(defn timestamp []
  (System/currentTimeMillis))

(def ^{:private true} m (atom 0))
(defn inc-m [amount]
  (swap! m + amount))

(defn get-m []
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
