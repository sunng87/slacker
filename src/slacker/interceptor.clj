(ns slacker.interceptor)

(defmacro definterceptor
  [interceptor-name & clauses]
  (if (odd? (count clauses))
    `(throw IllegalArgumentException. "Invalid clause for definterceptor"))
  `(def ~interceptor-name
     (hash-map ~@clauses)))

(defmacro interceptors
  [intercs]
  `{:before #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :before identity) y#))))
                          intercs))
    :after #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :after identity) y#))))
                         (reverse intercs)))})

