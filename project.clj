(defproject funcool/catacumba "0.2.0"
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

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.ratpack/ratpack-core "0.9.17" :exclusions [io.netty/netty-codec-http
                                                                io.netty/netty-handler
                                                                io.netty/netty-transport-native-epoll]]
                 [io.netty/netty-all "4.1.0.Beta5"]
                 [org.slf4j/slf4j-simple "1.7.12"]
                 [cheshire "5.5.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [buddy/buddy-core "0.5.0"]
                 [buddy/buddy-auth "0.5.3"]
                 [funcool/cuerdas "0.4.0"]
                 [funcool/futura "0.2.0"]
                 [environ "1.0.0"]
                 [potemkin "0.3.13" :exclusions [riddley]]]
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
                                       catacumba.handlers.auth
                                       catacumba.handlers.parsing
                                       catacumba.handlers.security
                                       catacumba.handlers.session
                                       catacumba.handlers.interceptor]
                             :language :clojure
                             :output-dir "doc/api"
                             :src-dir-uri "http://github.com/funcool/catacumba/blob/master/"
                             :src-linenum-anchor-prefix "L"}
                   :plugins [[funcool/codeina "0.1.0" :exclusions [org.clojure/clojure]]
                             [lein-ancient "0.6.7" :exclusions [org.clojure/tools.reader]]]
                   :dependencies [[clj-http "1.1.2"]
                                  [aleph "0.4.0" :exclusions [io.netty/netty-all]]

                                  [org.clojure/tools.namespace "0.2.10"]
                                  [ring/ring-core "1.3.2"
                                   :exclusions [javax.servlet/servlet-api
                                                clj-time
                                                org.clojure/clojure]]]}})
