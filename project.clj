(defproject slacker "0.12.1"
  :description "Transparent, non-invasive RPC by clojure and for clojure"
  :url "http://github.com/sunng87/slacker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [link "0.6.13"]
                 [info.sunng/carbonite "0.2.3"]
                 [cheshire "5.3.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [com.taoensso/nippy "2.7.0-alpha1"]]
  :profiles {:example {:source-paths ["examples"]
                       :dependencies [[org.clojure/java.jmx "0.2.0"]]}}
  :plugins [[lein-exec "0.3.1"]
            [codox "0.6.7"]]
  :global-vars {*warn-on-reflection* true}
  :aliases {"run-example-server" ["with-profile" "default,example" "run" "-m" "slacker.example.server"]
            "run-example-client" ["with-profile" "default,example" "run" "-m" "slacker.example.client"]}
  :deploy-repositories {"releases" :clojars})
