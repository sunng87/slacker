(ns slacker.client
  (:require [clojure.string :refer [split]]
            [link.tcp :onlu [stop-clients]]
            [slacker.common :refer :all :as c]
            [slacker.client.common :refer :all]
            [slacker.interceptor :as interceptor]))

(defonce ^{:doc "the default client factory using plain socket"}
  cached-slacker-client-factory
  (delay (create-client-factory nil)))

(defn slacker-client-factory
  "Create a secure client factory from ssl-connect"
  [ssl-context]
  (create-client-factory ssl-context))

(defn slackerc
  "Create connection to a slacker server."
  [addr
   & {:keys [content-type factory ping-interval timeout backlog
             interrupt-on-timeout interceptors callback-executor
             protocol-version]
      :or {content-type :clj
           interceptors interceptor/default-interceptors}}]
  (let [factory (or factory @cached-slacker-client-factory)]
    (delay (create-client factory addr content-type
                          {:timeout timeout
                           :backlog backlog
                           :ping-interval ping-interval
                           :interrupt-on-timeout interrupt-on-timeout
                           :interceptors interceptors
                           :callback-executor callback-executor
                           :protocol-version protocol-version}))))

(defn close-slackerc
  "Close a slacker client"
  [client]
  (when (realized? client)
    (close @client)))

(defn shutdown-slacker-client-factory
  "Shutdown a client factory, close all clients derived from it."
  ([] (shutdown-factory @cached-slacker-client-factory))
  ([factory] (shutdown-factory factory)))

(defmacro defn-remote
  "Define a facade for remote function. You have to provide the
  connection and the function name. (Argument list is not required here.)"
  ([sc fname & {:keys [remote-ns remote-name async? fire-and-forget?
                       callback extensions]
                :or {remote-ns (ns-name *ns*)
                     remote-name nil
                     async? false
                     fire-and-forget? false
                     callback nil}
                :as options}]
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
              (apply invoke-slacker ~sc
                     [~remote-ns ~remote-name (into [] args#)]
                     (mapcat vec (into [] ~options))))
            {:slacker-remote-fn true
             :slacker-client ~sc
             :slacker-remote-ns ~remote-ns
             :slacker-remote-name ~remote-name})))))

(defn- defn-remote*
  [sc-sym fname]
  (eval (list 'slacker.client/defn-remote sc-sym (symbol fname))))

(defn call-remote
  "call a remote function by its namespace and function name, without a
  local `defn-remote` reference.

  * `sc` the slacker client
  * `remote-ns` remote namespace, string
  * `remote-fn` remote function name, string
  * `args` arguments, vec
  * `:async? true` make this a async function, returns a manifold deferred
  * `:callback (fn [r e] )` set a callback for this async function
  * `:extensions {}` add extension data for the function call"
  [sc remote-ns remote-fn args
   & {:keys [async? callback extensions]
      :as options}]
  (apply invoke-slacker sc [remote-ns remote-fn args]
         (mapcat vec (into [] options))))

(defn use-remote
  "import remote functions the current namespace, this function
  will generate remote call, use it carefully in a declarative style."
  ([sc-sym] (use-remote sc-sym (ns-name *ns*)))
  ([sc-sym rns] (use-remote sc-sym rns nil))
  ([sc-sym rns lns & {:keys [only exclude]
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
           all-functions (functions-remote @(resolve sc-sym) (str rns))]
       (binding [*ns* (or lns *ns*)]
           (dorun (map defn-remote*
                    (repeat sc-sym)
                    (filter filter-fn all-functions)))))))

(defmacro with-slackerc
  "call the slacker remote function with a client other than the client
  used to declare the function"
  [sc & body]
  `(binding [*sc* ~sc] ~@body))

(defmacro with-callback
  "call the slacker remote function with a custom callback, and make the
  function an async one"
  [& body]
  (let [cb (last body)
        body (drop-last body)]
    `(binding [*callback* ~cb] ~@body)))

(defn slacker-meta
  "Fetch metadata of a slacker function."
  [f]
  (let [metadata (meta f)
        {sc :slacker-client
         remote-ns :slacker-remote-ns
         remote-fn :slacker-remote-name} metadata]
    (if sc
      (merge metadata
             (meta-remote sc (str remote-ns "/" remote-fn)))
      metadata)))

(defn slacker-server-status
  "Fetch server status of current slacker server."
  [sc]
  (clients-remote sc))
