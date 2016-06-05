(defproject funcool/catacumba "0.17.0"
  :description "Asynchronous Web Toolkit for Clojure."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|bench\.clj|user\.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"
                  "-Xlint:unchecked" "-Xlint:deprecation"]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.21" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [io.ratpack/ratpack-core "1.3.3"
                  :exclusions [io.netty/netty-codec-http
                               io.netty/netty-handler
                               io.netty/netty-transport-native-epoll]]
                 [io.netty/netty-all "4.1.0.CR7"]
                 [cheshire "5.6.1"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ns-tracker "0.3.0"]
                 [manifold "0.1.4"]
                 [com.stuartsierra/component "0.3.1"]
                 [commons-io/commons-io "2.5"]
                 [buddy/buddy-sign "1.0.0"]
                 [funcool/cuerdas "0.7.2"]
                 [funcool/promesa "1.2.0"]
                 [danlentz/clj-uuid "0.1.6"]
                 [environ "1.0.3"]
                 [com.cognitect/transit-clj "0.8.285"]])
