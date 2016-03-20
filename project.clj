(defproject funcool/catacumba "0.11.3-SNAPSHOT"
  :description "Asynchronous Web Toolkit for Clojure."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|bench\.clj|user\.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"
                  "-Xlint:unchecked"]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.19" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [io.ratpack/ratpack-core "1.2.0"
                  :exclusions [io.netty/netty-codec-http
                               io.netty/netty-handler
                               io.netty/netty-transport-native-epoll]]
                 [io.netty/netty-all "4.1.0.Beta8"]
                 [cheshire "5.5.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ns-tracker "0.3.0"]
                 [manifold "0.1.2" :exclusions [riddley]]
                 [com.stuartsierra/component "0.3.1"]
                 [commons-io/commons-io "2.4"]
                 [buddy/buddy-sign "0.9.0" :exclusions [org.clojure/tools.reader]]
                 [funcool/cuerdas "0.7.1"]
                 [funcool/promesa "1.1.1"]
                 [danlentz/clj-uuid "0.1.6"]
                 [environ "1.0.2"]
                 [potemkin "0.4.3"]
                 [com.cognitect/transit-clj "0.8.285"]])
