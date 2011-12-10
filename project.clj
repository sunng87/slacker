(defproject info.sunng/slacker "0.2.0"
  :description "Clojure RPC"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [aleph "0.2.0"]
                 [info.sunng/carbonite "0.1.0"]
                 [clj-json "0.4.3"]
                 [commons-pool/commons-pool "1.5.6"]]
  :dev-dependencies [[codox "0.2.3"]
                     [lein-exec "0.1"]]
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
              :client "slacker.example.client"}
  :aot [slacker.SlackerException])


