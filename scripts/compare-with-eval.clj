(println "Some comparisons between remote eval and slacker.")
(use 'slacker.serialization)

(def params (serialize :carb [100 200 300 400 500 600 700 800 900]))
(def params2 (serialize :carb [{:a 2 :b 3} 100 "tomcat"]))

(defn reenterable-read [buf]
  (let [result (deserialize :carb buf)]
    (.rewind buf)
    result))

(defn a-complex-function [m i s]
  (str (* i (:a m)) " * " s))

(def funcs {:plus + :s a-complex-function})

(println "Simple function with 9 arguments (+ 100 200 300 ...)")
(println "---------------")
(println "nREPL (eval):")
(time (dotimes [_ 10000] (eval '(+ 100 200 300 400 500 600 700 800 900))))
(println "slacker (direct function call + deserialization):")
(time (dotimes [_ 10000] (apply (:plus funcs) (reenterable-read params))))

(println "A complex function with map, number and string as arguments:")
(println "----------------")
(println "nREPL (eval):")
(time (dotimes [_ 10000] (eval '(a-complex-function {:a 2 :b 3} 100 "tomcat"))))
(println "slacker (direct function call + deserialization):")
(time (dotimes [_ 10000] (apply (:s funcs) (reenterable-read params2))))

