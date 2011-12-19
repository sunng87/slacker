(defproject info.sunng/slacker "0.4.0-SNAPSHOT"
  :description "Clojure RPC"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [aleph "0.2.1-SNAPSHOT"]
                 [info.sunng/carbonite "0.2.0"]
                 [cheshire "2.0.4"]
                 [commons-pool/commons-pool "1.5.6"]
                 [org.clojure/java.jmx "0.1"]
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[codox "0.3.0"]
                     [lein-exec "0.1"]
                     [lein-multi "1.0.0"]]
  :multi-deps {"1.2" [[org.clojure/clojure "1.2.1"]
                      [aleph "0.2.0"]
                      [info.sunng/carbonite "0.1.1"]
                      [cheshire "2.0.4"]
                      [commons-pool/commons-pool "1.5.6"]
                      [org.clojure/java.jmx "0.1"]
                      [org.clojure/tools.logging "0.2.3"]]}
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
              :client "slacker.example.client"}
  :aot [slacker.SlackerException])


