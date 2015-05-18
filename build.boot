(set-env!
 :source-paths #{"src/clojure" "src/java" "test"}
 :resource-paths #{"resources"}
 :dependencies '[;; Current runtime dependencies
                 [org.clojure/clojure "1.7.0-beta3" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.12" :scope "provided"]

                 ;; Boot dependencies
                 [boot-deps "0.1.4" :scope "test"]
                 [funcool/bootutils "0.1.0" :scope "test"]
                 [funcool/boot-codeina "0.1.0-SNAPSHOT" :scope "test"]
                 [adzerk/boot-test "1.0.4" :scope "test"]

                 ;; Testing dependecines
                 [clj-http "1.1.2" :scope "test"]
                 [cc.qbits/jet "0.6.2" :scope "test"]
                 [org.clojure/tools.namespace "0.2.10" :scope "test"]
                 [ring/ring-core "1.3.2"
                  :scope "test"
                  :exclusions [javax.servlet/servlet-api
                               org.clojure/clojure
                               clj-time]]

                 ;; Required dependencies
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.ratpack/ratpack-core "0.9.16"]
                 [com.stuartsierra/component "0.2.3"]
                 [buddy/buddy-core "0.5.0"]
                 [buddy/buddy-auth "0.5.3"]
                 [cats "0.4.0"]
                 [funcool/cuerdas "0.4.0"]
                 [funcool/futura "0.1.0-alpha2" :exclusions [org.reactivestreams/reactive-streams]]
                 [environ "1.0.0"]
                 [potemkin "0.3.13" :exclusions [riddley]]])

(require
 '[funcool.bootutils :refer :all]
 '[funcool.boot-codeina :refer :all]
 '[adzerk.boot-test :refer :all]
 '[boot-deps :refer [ancient]])

(def +version+ "0.2.0-SNAPSHOT")

(task-options!
 pom {:project     'funcool/catacumba
      :version     +version+
      :description "Asynchronous web toolkit for Clojure build on top of Ratpack."
      :url         "http://github.com/funcool/catacumba"
      :scm         {:url "https://github.com/funcool/catacumba"}
      :license     {"BSD (2 Clause)" "http://opensource.org/licenses/BSD-2-Clause"}}

 apidoc {:exclude ['catacumba.impl.context
                   'catacumba.impl.helpers
                   'catacumba.impl.parse
                   'catacumba.impl.handlers
                   'catacumba.impl.server
                   'catacumba.impl.http
                   'catacumba.impl.routing
                   'catacumba.impl.streams
                   'catacumba.impl.websocket
                   'catacumba.impl.sse
                   'catacumba.impl.types
                   'catacumba.handlers.core
                   'catacumba.handlers.cors
                   'catacumba.handlers.security
                   'catacumba.handlers.session
                   'catacumba.handlers.interceptor
                   'catacumba.experimental.stomp
                   'catacumba.experimental.stomp.parser
                   'catacumba.experimental.stomp.broker]
         :sources #{"src/clojure"}
         :reader :clojure
         :writer :html5
         :target "doc/api"
         :src-uri "http://github.com/funcool/catacumba/blob/master/"
         :src-uri-prefix "#L"})


(deftask doc
  []
  (comp (javac)
        (apidoc)))
