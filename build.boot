(set-env!
 :source-paths #{"src/clojure" "src/java" "test"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.7.0-beta3" :scope "provided"]
                 [org.slf4j/slf4j-simple "1.7.10" :scope "provided"]

                 [funcool/bootutils "0.1.0-SNAPSHOT" :scope "test"]
                 [adzerk/boot-test "1.0.4" :scope "test"]
                 [clj-http "1.1.0" :scope "test"]
                 [cc.qbits/jet "0.6.1" :scope "test"]
                 [org.clojure/tools.namespace "0.2.10" :scope "test"]
                 [ring/ring-core "1.3.2"
                  :exclusions [javax.servlet/servlet-api
                               clj-time
                               org.clojure/clojure]
                  :scope "test"]

                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.ratpack/ratpack-core "0.9.16"]
                 [com.stuartsierra/component "0.2.3"]
                 [buddy/buddy-core "0.5.0"]
                 [buddy/buddy-auth "0.5.3"]
                 [funcool/cuerdas "0.4.0"]
                 [funcool/futura "0.1.0-alpha2" :exclusions [org.reactivestreams/reactive-streams]]
                 [environ "1.0.0"]
                 [potemkin "0.3.12" :exclusions [riddley]]])

(require
 '[bootutils.core :refer :all]
 '[adzerk.boot-test :refer :all])

(def +version+ "0.2.0-SNAPSHOT")

(task-options!
 pom {:project     'funcool/catacumba
      :version     +version+
      :description "Asynchronous web toolkit for Clojure build on top of Ratpack."
      :url         "http://github.com/funcool/catacumba"
      :scm         {:url "https://github.com/funcool/catacumba"}
      :license     {"BSD (2 Clause)" "http://opensource.org/licenses/BSD-2-Clause"}})
