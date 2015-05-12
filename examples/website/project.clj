(defproject website "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.ratpack/ratpack-core "0.9.16"]
                 [org.slf4j/slf4j-simple "1.7.10"]
                 [com.stuartsierra/component "0.2.3"]
                 [buddy/buddy-core "0.5.0"]
                 [buddy/buddy-auth "0.5.3-SNAPSHOT"]
                 [funcool/cuerdas "0.4.0"]
                 [funcool/futura "0.1.0-alpha2"
                  :exclusions [org.reactivestreams/reactive-streams]]
                 [environ "1.0.0"]
                 [potemkin "0.3.12" :exclusions [riddley]]
                 [cheshire "5.4.0"]
                 [hiccup "1.0.5"]]
  :source-paths ["src", "/home/niwi/devel/catacumba/src/clojure"]
  :java-source-paths ["/home/niwi/devel/catacumba/src/java"]

  :main ^:skip-aot website.core
  ;; :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
