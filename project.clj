(defproject slacker "0.17.1-SNAPSHOT"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[link "0.12.4"]
                 [rigui "0.5.2"]
                 [manifold "0.1.8"]
                 [org.clojure/tools.logging "0.4.1"]
                 [trptcolin/versioneer "0.2.0"]]
  :profiles {:example {:source-paths ["examples"]
                       :dependencies [[org.clojure/java.jmx "0.3.4"]]}
             :dev {:dependencies [[org.clojure/clojure "1.10.0"]
                                  [cheshire "5.8.1"]
                                  [com.taoensso/nippy "2.14.0"
                                   :exclusions [org.clojure/clojure]]
                                  [com.cognitect/transit-clj "0.8.313"]
                                  [log4j "1.2.17"]]}
             :clojure18 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :clojure19 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :clojure110 {:dependencies [[org.clojure/clojure "1.8.0"]]}}
  :plugins [[lein-exec "0.3.1"]
            [lein-codox "0.9.5"]]
  :global-vars {*warn-on-reflection* true}
  :aliases {"run-example-server" ["trampoline" "with-profile" "default,clojure19,example" "run" "-m" "slacker.example.server"]
            "run-example-client" ["trampoline" "with-profile" "default,clojure19,example" "run" "-m" "slacker.example.client"]
            "run-bench" ["trampoline" "with-profiles" "default,clojure19,example" "run" "-m" "slacker.example.bench"]
            "test-all" ["with-profile" "default,clojure18:default,clojure19" "test"]}
  :deploy-repositories {"releases" :clojars}
  :codox {:output-path "target/codox"
          :source-uri "https://github.com/sunng87/slacker/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :jvm-opts ["-Dio.netty.leakDetection.level=advanced"
             "-Dio.netty.leakDetection.targetRecords=20"])
