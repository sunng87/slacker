(ns slacker.client
  (:use [slacker common])
  (:use [slacker.client common])
  (:use [clojure.string :only [split]])
  (:use [link.tcp :only [stop-clients]]))

(defonce slacker-client-factory (atom nil))

(defn slackerc
  "Create connection to a slacker server."
  [addr
   & {:keys [content-type ssl-context]
      :or {content-type :carb
           ssl-context nil}
      :as _}]
  (let [factory (or @slacker-client-factory
                    (swap! slacker-client-factory (fn [_] (create-client-factory ssl-context))))
        [host port] (host-port addr)]
    (create-client factory host port content-type)))

(defn close-slackerc [client]
  (close client))

(defn close-all-slackerc []
  (when @slacker-client-factory
    (stop-clients @slacker-client-factory)))

(defmacro defn-remote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  ([sc fname & {:keys [remote-ns remote-name async? callback]
                :or {remote-ns (ns-name *ns*)
                     remote-name nil
                     async? false callback nil}}]
     (let [fname-str (str fname)
           remote-ns-declared (> (.indexOf fname-str "/") 0)
           [remote-ns remote-name] (if remote-ns-declared
                                     (split fname-str #"/" 2)
                                     [remote-ns
                                      (or remote-name fname-str)])
           facade-sym (if remote-ns-declared
                        (symbol remote-name)
                        fname)]
       `(def ~facade-sym
          (with-meta
            (fn [& args#]
              (invoke-slacker ~sc
                              [~remote-ns ~remote-name (into [] args#)]
                              :async? ~async?
                              :callback ~callback))
            (merge (meta-remote ~sc (str ~remote-ns "/" ~remote-name))
                   {:slacker-remote-fn true
                    :slacker-client ~sc
                    :slacker-remote-ns ~remote-ns
                    :slacker-remote-name ~remote-name}))))))

(defn- defn-remote*
  [sc-sym fname]
  (eval (list 'defn-remote sc-sym (symbol fname))))

(defn use-remote
  "import remote functions the current namespace"
  ([sc-sym] (use-remote sc-sym (ns-name *ns*)))
  ([sc-sym rns & {:keys [only exclude]
                  :or {only [] exclude []}}]
     (if (and (not-empty only) (not-empty exclude))
       (throw (IllegalArgumentException.
               "do not provide :only and :exclude both")))
     (let [name-fn #(str rns "/" %)
           filter-fn (cond
                      (not-empty only)
                      #(contains? (set (map name-fn only)) %)
                      (not-empty exclude)
                      #(not (contains? (set (map name-fn exclude)) %))
                      :else (constantly true))
           all-functions (inspect @(resolve sc-sym) :functions (str rns))]
       (dorun (map defn-remote*
                   (repeat sc-sym)
                   (filter filter-fn all-functions))))))
