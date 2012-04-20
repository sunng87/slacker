(defproject slacker "0.8.0-SNAPSHOT"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [link "0.3.1"]
                 [info.sunng/carbonite "0.2.2"]
                 [cheshire "3.0.0"]
                 [slingshot "0.10.1"]
                 [org.clojure/java.jmx "0.1"]                 
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[codox "0.6.1"]
                     [lein-exec "0.1"]
                     [criterium "0.2.1-SNAPSHOT"]]
  :extra-classpath-dirs ["examples"]
  :run-aliases {:server "slacker.example.server"
                :client "slacker.example.client"}
  :warn-on-reflection false)


