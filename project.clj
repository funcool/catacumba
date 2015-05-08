(defproject funcool/catacumba "0.2.0-SNAPSHOT"
  :description "Asynchronous web toolkit for Clojure build on top of Ratpack."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  ;; :mirrors {"central" {:name "central"
  ;;                      :url "http://oss.jfrog.org/artifactory/repo"}}

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
                 [potemkin "0.3.12" :exclusions [riddley]]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                   :source-paths ["src"]
                   :codeina {:sources ["src/clojure"]
                             :exclude [catacumba.impl.context
                                       catacumba.impl.helpers
                                       catacumba.impl.parse
                                       catacumba.impl.handlers
                                       catacumba.impl.server
                                       catacumba.impl.http
                                       catacumba.impl.routing
                                       catacumba.impl.streams
                                       catacumba.impl.websocket
                                       catacumba.impl.sse
                                       catacumba.impl.types
                                       catacumba.handlers.core
                                       catacumba.handlers.cors
                                       catacumba.handlers.security
                                       catacumba.handlers.session
                                       catacumba.handlers.interceptor
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
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [ring/ring-core "1.3.2"
                                   :exclusions [javax.servlet/servlet-api
                                                org.clojure/clojure]]]}})
