(defproject slacker "0.8.0-SNAPSHOT"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [link "0.1.1-SNAPSHOT"]
                 [info.sunng/carbonite "0.2.2"]
                 [cheshire "2.2.0"]
                 [slingshot "0.10.1"]
                 [org.clojure/java.jmx "0.1"]                 
                 [zookeeper-clj "0.9.2"]
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[codox "0.5.0"]
                     [lein-exec "0.1"]]
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
                :client "slacker.example.client"
                :cluster-server "slacker.example.cluster-server"
                :cluster-client "slacker.example.cluster-client"}
  :warn-on-reflection false)


