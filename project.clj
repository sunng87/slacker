(defproject slacker "0.15.2-SNAPSHOT"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[link "0.10.3"]
                 [rigui "0.5.2"]
                 [manifold "0.1.6"]
                 [org.clojure/tools.logging "0.4.0"]]
  :profiles {:example {:source-paths ["examples"]
                       :dependencies [[org.clojure/java.jmx "0.3.4"]]}
             :dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [cheshire "5.8.0"]
                                  [com.taoensso/nippy "2.13.0"
                                   :exclusions [org.clojure/clojure]]
                                  [com.cognitect/transit-clj "0.8.300"]
                                  [log4j "1.2.17"]]}
             :clojure15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :clojure16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :clojure17 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :clojure18 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :clojure19 {:dependencies [[org.clojure/clojure "1.9.0-alpha10"]]}}
  :plugins [[lein-exec "0.3.1"]
            [lein-codox "0.9.5"]]
  :global-vars {*warn-on-reflection* true}
  :aliases {"run-example-server" ["trampoline" "with-profile" "default,clojure18,example" "run" "-m" "slacker.example.server"]
            "run-example-client" ["trampoline" "with-profile" "default,clojure18,example" "run" "-m" "slacker.example.client"]
            "run-bench" ["trampoline" "with-profiles" "default,clojure18,example" "run" "-m" "slacker.example.bench"]
            "test-all" ["with-profile" "default,clojure15:default,clojure16:default,clojure17:default,clojure18" "test"]}
  :deploy-repositories {"releases" :clojars}
  :codox {:output-path "target/codox"
          :source-uri "https://github.com/sunng87/slacker/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}})
