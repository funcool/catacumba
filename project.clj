(defproject funcool/catacumba "0.6.0-SNAPSHOT"
  :description "Asynchronous Web Toolkit for Clojure."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|bench\.clj|user\.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options" "-Xlint:unchecked"]

  :repositories [["clojars" {:sign-releases false}]]
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.12" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.ratpack/ratpack-core "0.9.19"
                  :exclusions [io.netty/netty-codec-http
                               io.netty/netty-handler
                               io.netty/netty-transport-native-epoll]]
                 [io.netty/netty-all "4.1.0.Beta5"]
                 [cheshire "5.5.0"]
                 [ns-tracker "0.3.0"]
                 [slingshot "0.12.2"]
                 [com.stuartsierra/component "0.2.3"]
                 [buddy/buddy-sign "0.6.1"]
                 [funcool/cuerdas "0.6.0"]
                 [funcool/promissum "0.2.0"]
                 [funcool/cats "0.6.1"]
                 [danlentz/clj-uuid "0.1.6"]
                 [environ "1.0.0"]
                 [potemkin "0.4.1"]])
