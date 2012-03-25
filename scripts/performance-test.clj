(use 'slacker.client)
(import '[java.util.concurrent Executors CountDownLatch])


(def total-calls (Integer/valueOf (second *command-line-args*)))
(def total-threads (Integer/valueOf (nth *command-line-args* 2)))

(println (str "Performing " total-calls " requests with "
              total-threads " threads"))

(def thread-pool (Executors/newFixedThreadPool total-threads))

(def scp (slackerc "127.0.0.1:2104"))
(defn-remote scp rand-ints :remote-ns "slacker.example.api")

(def cdl (CountDownLatch. total-calls))
(time
 (do
   (.invokeAll thread-pool
               (take total-calls (repeat
                                  (fn [] (do
                                          (rand-ints 5)
                                          (.countDown cdl))))))
   (.await cdl)))

(System/exit 0)

