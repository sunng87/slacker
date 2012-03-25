(ns slacker.client
  (:use [slacker common])
  (:use [slacker.client common])
  (:use [clojure.string :only [split]])
  (:import [slacker.client.common SlackerClient])
  (:import [slacker.client.pool PooledSlackerClient]))

(defn slackerc
  "Create connection to a slacker server."
  [addr
   & {:keys [content-type]
      :or {content-type :carb}
      :as _}]
  (let [[host port] (host-port addr)]
    (create-client host port content-type)))


(defn close-slackerc [client]
  (close client))


(defmacro defn-remote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  ([sc fname & {:keys [remote-ns remote-name async? callback]
                :or {remote-ns (ns-name *ns*)
                     remote-name nil async? false callback nil}}]
     `(let [rname# (or ~remote-name (name '~fname))]
        (def ~fname
          (with-meta
            (fn [& args#]
              (invoke-slacker ~sc
                [~remote-ns rname# (into [] args#)]
                :async? ~async?
                :callback ~callback))
            (merge (meta-remote ~sc (str ~remote-ns "/" rname#))
                   {:slacker-remote-fn true
                    :slacker-client ~sc
                    :slacker-remote-ns ~remote-ns
                    :slacker-remote-name rname#}))))))

(defn- defn-remote*
  [sc-sym fname]
  (eval (list 'defn-remote sc-sym
              (symbol (second (split fname #"/")))
              :remote-ns (first (split fname #"/")))))

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

