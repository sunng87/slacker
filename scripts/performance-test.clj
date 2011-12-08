(use 'slacker.client)

(def sc (slackerc "localhost" 2104))
(def scp (slackerc-pool "localhost" 2104))

(defremote sc rand-ints2 :remote-name "rand-ints")
(defremote scp rand-ints)

(def total-calls (Integer/valueOf (second *command-line-args*)))
(println (str "Performing " total-calls " requests on the server"))
(time (dorun (pmap rand-ints (take total-calls (repeat 5)))))

;(time (dorun (map rand-ints (take total-calls (repeat 5)))))
(System/exit 0)

