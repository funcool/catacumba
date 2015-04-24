(defproject funcool/catacumba "0.1.0-alpha1"
  :description "Asynchronous web toolkit for Clojure build on top of Ratpack."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src" "src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [io.ratpack/ratpack-core "0.9.15"]
                 [org.slf4j/slf4j-simple "1.7.10"]
                 [funcool/futura "0.1.0-SNAPSHOT"
                  :exclusions [org.reactivestreams/reactive-streams]]
                 [environ "1.0.0"]
                 [potemkin "0.3.12"]
                 [funcool/cuerdas "0.4.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                                 ;; *unchecked-math* :warn-on-boxed}
                   :codeina {:sources ["src"]
                             :exclude [catacumba.impl.context
                                       catacumba.impl.helpers
                                       catacumba.impl.parse
                                       catacumba.impl.handlers
                                       catacumba.impl.server
                                       catacumba.impl.http
                                       catacumba.impl.routing
                                       catacumba.impl.streams
                                       catacumba.impl.websocket
                                       catacumba.experimental.stomp
                                       catacumba.experimental.stomp.parser
                                       catacumba.experimental.stomp.broker]
                             :language :clojure
                             :output-dir "doc/api"
                             :src-dir-uri "http://github.com/funcool/catacumba/blob/master/"
                             :src-linenum-anchor-prefix "L"}
                   :plugins [[funcool/codeina "0.1.0-SNAPSHOT"
                              :exclusions [org.clojure/clojure]]]
                   :dependencies [[clj-http "1.1.0"]
                                  [cc.qbits/jet "0.6.1"]
                                  [ring/ring-core "1.3.2"
                                   :exclusions [javax.servlet/servlet-api
                                                org.clojure/clojure]]]}})
