(defproject org.clojars.pauld/slacker "0.8.5"
  :description "Transparent, non-invasive RPC by clojure and for clojure."
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [link "0.3.3"]
                 [info.sunng/carbonite "0.2.3"]
                 [cheshire "4.0.3"]
                 [slingshot "0.10.3"]
                 [org.clojure/java.jmx "0.2.0"]
                 [org.clojure/tools.logging "0.2.4"]]
  :profiles {:dev {:resource-paths ["examples"]
                   :dependencies [[codox "0.6.1"]]}
             :1.3 {:dependencies [org.clojure/clojure "1.3.0"]}}
  :plugins [[lein-exec "0.2.0"]]
  :warn-on-reflection true
  :aliases {"run-example-server" ["run" "-m" "slacker.example.server"]
            "run-example-client" ["run" "-m" "slacker.example.client"]})


