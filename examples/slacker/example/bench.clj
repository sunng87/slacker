(ns slacker.example.bench
  (:require [slacker.client :refer :all])
  (:import [java.util.concurrent Executors CountDownLatch]))

(def conn (slackerc "127.0.0.1:2104" :content-type :nippy))

(defn-remote conn slacker.example.api/rand-ints)

(defn -main [& args]
  (let [task-count 500000
        pool (Executors/newFixedThreadPool 500)
        counter (CountDownLatch. task-count)
        task (fn [] (rand-ints 40) (.countDown counter))
        tasks (take task-count (repeat task))]
    (time
     (do
       (.invokeAll pool tasks)
       (.await counter)))

    (shutdown-slacker-client-factory)
    (shutdown-agents)))
