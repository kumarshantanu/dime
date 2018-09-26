(defproject dime "0.5.1"
  :description "Dependency Injection Made Easy for Clojure"
  :url "https://github.com/kumarshantanu/dime"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                *unchecked-math* :warn-on-boxed
                *assert* true}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}
             :c05 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c06 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c07 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c08 {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c09 {:dependencies [[org.clojure/clojure "1.9.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c10 {:dependencies [[org.clojure/clojure "1.10.0-alpha7"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :plugins [[lein-viz "0.3.0"]]
  :viz {:default {:source foo.init/viz-payload}})
