(defproject funcool/catacumba "2.2.1"
  :description "Ratpack Based Asynchronous Web Toolkit for Clojure."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|bench\.clj|user\.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"
                  "-Xlint:unchecked" "-Xlint:deprecation"]
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.25" :scope "provided"]
                 [org.clojure/core.async "0.4.474"]
                 [io.ratpack/ratpack-core "1.5.4"
                  :exclusions [[io.netty/netty-codec-http]
                               [io.netty/netty-handler]
                               [io.netty/netty-transport-native-epoll]
                               [org.yaml/snakeyaml]
                               [com.fasterxml.jackson.core/jackson-databind]
                               [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml]
                               [com.fasterxml.jackson.datatype/jackson-datatype-guava]
                               [com.fasterxml.jackson.datatype/jackson-datatype-jdk8]
                               [com.fasterxml.jackson.datatype/jackson-datatype-jsr310]]]

                 [io.netty/netty-all "4.1.25.Final"]

                 [cheshire "5.8.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                               com.fasterxml.jackson.dataformat/jackson-dataformat-smile]]

                 ;; Updated & forced dependencies of jackson (form cheshire & ratpack)
                 [com.fasterxml.jackson.core/jackson-core "2.9.5"]
                 [com.fasterxml.jackson.core/jackson-databind "2.9.5"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.9.5"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.9.5"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml "2.9.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-jdk8 "2.9.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.9.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-guava "2.9.5"
                  :exclusions [[org.yaml/snakeyaml]
                               [com.google.guava/guava]]]

                 [ns-tracker "0.3.1"]
                 [manifold "0.1.6"]
                 [commons-io/commons-io "2.6"]
                 [buddy/buddy-sign "2.2.0"]
                 [funcool/beicon "4.1.0"]
                 [funcool/cuerdas "2.0.5"]
                 [funcool/promesa "1.9.0"]
                 [danlentz/clj-uuid "0.1.7"]
                 [environ "1.1.0"]
                 [com.cognitect/transit-clj "0.8.309"]])
