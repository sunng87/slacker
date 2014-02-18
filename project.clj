(defproject slacker "0.10.1"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [link "0.5.4"]
                 [info.sunng/carbonite "0.2.3"]
                 [cheshire "5.3.1"]
                 [slingshot "0.10.3"]
                 [org.clojure/java.jmx "0.2.0"]
                 [org.clojure/tools.logging "0.2.6"]]
  :profiles {:dev {:source-paths ["examples"]}
             :1.3 {:dependencies [org.clojure/clojure "1.3.0"]}}
  :plugins [[lein-exec "0.3.1"]
            [codox "0.6.7"]]
  :global-vars {*warn-on-reflection* true}
  :aliases {"run-example-server" ["with-profile" "default,dev" "run" "-m" "slacker.example.server"]
            "run-example-client" ["with-profile" "default,dev" "run" "-m" "slacker.example.client"]})
