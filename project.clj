(defproject slacker "0.4.0-SNAPSHOT"
  :description "Clojure RPC based on serialization and TCP"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [aleph "0.2.0"]
                 [info.sunng/carbonite "0.1.1"]
                 [clj-json "0.4.3"]
                 [commons-pool/commons-pool "1.5.6"]
                 [slingshot "0.10.0"]]
  :dev-dependencies [[codox "0.3.3"]
                     [lein-exec "0.1"]
                     [lein-sub "0.1.1"]]
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
                :client "slacker.example.client"})


