(defproject slacker "0.7.0"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [aleph "0.2.0"]
                 [info.sunng/carbonite "0.2.2"]
                 [cheshire "2.2.0"]
                 [commons-pool/commons-pool "1.5.6"]
                 [slingshot "0.10.1"]
                 [zookeeper-clj "0.9.2"
                  :exclusions [jline/jline
                               junit/junit]]
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[codox "0.5.0"]
                     [lein-exec "0.1"]]
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
                :client "slacker.example.client"
                :cluster-server "slacker.example.cluster-server"
                :cluster-client "slacker.example.cluster-client"})


