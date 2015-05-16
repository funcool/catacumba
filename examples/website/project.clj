(defproject website "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0-beta3"]
                 [funcool/catacumba "0.2.0-SNAPSHOT"]
                 [hiccup "1.0.5"]]
  :main ^:skip-aot website.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
