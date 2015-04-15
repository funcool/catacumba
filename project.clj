(defproject funcool/catacumba "0.1.0-SNAPSHOT"
  :description "Asynchronous web toolkit for Clojure build on top of Ratpack."
  :url "http://github.com/funcool/catacumba"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[io.ratpack/ratpack-core "0.9.15"]
                 [org.slf4j/slf4j-simple "1.7.10"]
                 [environ "1.0.0"]
                 [potemkin "0.3.12"]
                 [funcool/cuerdas "0.4.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                                 ;; *unchecked-math* :warn-on-boxed}
                   :codeina {:sources ["src"]
                             :exclude [catacumba.impl]
                             :language :clojure
                             :output-dir "doc/api"
                             :src-dir-uri "http://github.com/funcool/catacumba/blob/master/"
                             :src-linenum-anchor-prefix "L"}
                   :plugins [[funcool/codeina "0.1.0-SNAPSHOT"
                              :exclusions [org.clojure/clojure]]]
                   :dependencies [[org.clojure/clojure "1.7.0-alpha6"]
                                  [clj-http "1.1.0"]
                                  [cc.qbits/jet "0.6.1"]
                                  [ring/ring-core "1.3.2"
                                   :exclusions [javax.servlet/servlet-api
                                                org.clojure/clojure]]]}})
