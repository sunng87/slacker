(use 'slacker.client)
(use 'criterium.core)
(import '[java.util.concurrent Executors CountDownLatch])

(def total-calls (Integer/valueOf (second *command-line-args*)))
(def total-threads (Integer/valueOf (nth *command-line-args* 2)))
(def report-func
  (if (> (count *command-line-args*) 3)
    (keyword (nth *command-line-args* 3)) :time))

(println (str "Performing " total-calls " requests with "
              total-threads " threads"))

(def thread-pool (Executors/newFixedThreadPool total-threads))

(def scp (slackerc "127.0.0.1:2104"))
(defn-remote scp slacker.example.api/rand-ints)

(def cdl (CountDownLatch. total-calls))

(defn run-all []
  (do
    (.invokeAll thread-pool
                (take total-calls (repeat
                                   (fn [] (do
                                           (rand-ints 5)
                                           (.countDown cdl))))))
    (.await cdl)))

(if (= :time report-func)
  (time (run-all))
  (bench (run-all) :verbose))

(System/exit 0)

