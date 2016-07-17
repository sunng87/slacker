(ns slacker.interceptor)

(defmacro definterceptor
  "Define an interceptor. You can specify interceptor functions for each
  stage (:before, :after). "
  [interceptor-name & clauses]
  (if (odd? (count clauses))
    `(throw IllegalArgumentException. "Invalid clause for definterceptor"))
  `(def ~interceptor-name
     (hash-map ~@clauses)))

(defmacro definterceptor+
  "Parameterized definterceptor, you can configure the interceptor by
  passing arguments in."
  [interceptor-name args & clauses]
  (if (odd? (count clauses))
    `(throw IllegalArgumentException. "Invalid clause for definterceptor"))
  (if-not (vector? args)
    `(throw IllegalArgumentException. "Invalid bindings."))
  `(defn ~interceptor-name ~args
     (hash-map ~@clauses)))

(defmacro interceptors
  "This is a macro to combine multiple interceptors. If you have more that one
  interceptors, use this macro to combine them as one."
  [intercs]
  `{:pre #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :pre identity) y#))))
                       intercs))
    :before #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :before identity) y#))))
                          intercs))
    :after #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :after identity) y#))))
                         (reverse intercs)))
    :post #(-> % ~@(map (fn [x] `((fn [y#] ((get ~x :post identity) y#))))
                         (reverse intercs)))})

(def ^{:doc "default identity interceptor set"}
  default-interceptors
  {:pre identity
   :before identity
   :after identity
   :post identity})
