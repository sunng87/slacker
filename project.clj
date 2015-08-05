(defproject slacker "0.12.10-SNAPSHOT"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[link "0.8.4"]
                 [org.clojure/tools.logging "0.3.1"]]
  :profiles {:example {:source-paths ["examples"]
                       :dependencies [[org.clojure/java.jmx "0.3.0"]]}
             :dev {:dependencies [[info.sunng/carbonite "0.2.3"]
                                  [cheshire "5.4.0"]
                                  [com.taoensso/nippy "2.7.1"
                                   :exclusions [org.clojure/clojure]]]}
             :clojure15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :clojure16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :clojure17 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :plugins [[lein-exec "0.3.1"]
            [codox "0.6.7"]]
  :global-vars {*warn-on-reflection* true}
  :aliases {"run-example-server" ["with-profile" "default,clojure16,example" "run" "-m" "slacker.example.server"]
            "run-example-client" ["with-profile" "default,clojure16,example" "run" "-m" "slacker.example.client"]
            "test-all" ["with-profile" "default,clojure15:default,clojure16" "test"]}
  :deploy-repositories {"releases" :clojars}
  :codox {:output-dir "target/codox"})
