(use 'slacker.client)
(import '[java.util.concurrent Executors TimeUnit CountDownLatch])

(def total-calls (Integer/valueOf (second *command-line-args*)))
(def total-threads (Integer/valueOf (nth *command-line-args* 2)))
(def total-connections (Integer/valueOf (nth *command-line-args* 3)))
(println (str "Performing " total-calls " requests with "
              total-threads " threads"))

(def thread-pool (Executors/newFixedThreadPool total-threads))

(def scp (slackerc-pool "localhost" 2104
                        :max-active total-connections))

(def cdl (CountDownLatch. total-calls))
(defn received [_]
  (.countDown cdl))
(defremote scp rand-ints :callback received)

(time
 (do
   (.invokeAll thread-pool (take total-calls (repeat (fn [] (rand-ints 5)))))
   (.await cdl)))

(System/exit 0)
