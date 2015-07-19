(defproject compchat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]
                 [funcool/catacumba "0.4.0"]]
  :main ^:skip-aot compchat.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
