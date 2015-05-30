(defproject debugging "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [funcool/catacumba "0.2.0"]
                 [prone "0.8.1"]]
  :main ^:skip-aot debugging.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
