(ns slacker.acl.core)

(defn- ip-seg-contains?
  "Ip-segment A contains? B or not"
  [ip-seg-a ip-seg-b]
  (cond
   (.contains ip-seg-a "*")
   (.startsWith ip-seg-b (.substring ip-seg-a 0 (.indexOf ip-seg-a "*")))
   (= ip-seg-a ip-seg-b) true
   :else false))

(defn- ip-set-contains?
  "ip-set A contains? B or not"
  [ip-set-A ip-set-B]
  (cond
   (= ip-set-A :all) true
   (string? ip-set-B)  (true? (some true? (map ip-seg-contains? ip-set-A (repeat ip-set-B))))
   (every? true? (for [x ip-set-B] (some true? (map ip-seg-contains? ip-set-A (repeat x))))) true))

(defn authorize
  "authorize user to acess slacer server"
  [client-info rules]
  (let [allow-set (rules :allow)
        deny-set (rules :deny)
        flag (ip-set-contains? deny-set allow-set)
        ip-address (.. (client-info :remote-addr) getAddress getHostAddress)]
    (cond
     (empty? rules) false
     flag (ip-set-contains? allow-set ip-address)
     :else (and (ip-set-contains? allow-set ip-address) (not (ip-set-contains? deny-set ip-address))))  
    ))





