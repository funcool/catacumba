{:dev
 {:global-vars {*warn-on-reflection* false}
  :aliases {"test-all" ["with-profile" "dev,1.8:dev" "test"]}
  :source-paths ["dev"]
  :dependencies [[clj-http "1.1.2" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/clojurescript "1.7.189"]
                 [aleph "0.4.0" :exclusions [io.netty/netty-all]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring/ring-core "1.4.0" :exclusions [javax.servlet/servlet-api
                                                      org.clojure/tools.reader
                                                      org.clojure/clojure
                                                      clj-time]]]
  :plugins [[lein-ancient "0.6.7" :exclusions [org.clojure/tools.reader]]]}

 :doc
 {:plugins [[funcool/codeina "0.3.0"]]
  :codeina {:sources ["src/clojure"]
            :exclude [catacumba.impl.context
                      catacumba.impl.atomic
                      catacumba.impl.executor
                      catacumba.impl.stream
                      catacumba.impl.stream.common
                      catacumba.impl.stream.channel
                      catacumba.impl.stream.promise
                      catacumba.impl.stream.pushstream
                      catacumba.impl.handlers
                      catacumba.impl.server
                      catacumba.impl.http
                      catacumba.impl.routing
                      catacumba.impl.stream
                      catacumba.impl.websocket
                      catacumba.impl.sse
                      catacumba.handlers.cors
                      catacumba.handlers.auth
                      catacumba.handlers.autoreload
                      catacumba.handlers.parsing
                      catacumba.handlers.security
                      catacumba.handlers.session
                      catacumba.handlers.interceptor]
            :reader :clojure
            :target "doc/dist/latest/api"
            :src-uri "http://github.com/funcool/catacumba/blob/master/"
            :src-uri-prefix "#L"}}

 :bench
 [:dev
  {:jvm-opts ^:replace ["-XX:+AggressiveOpts"
                        "-XX:+UseG1GC"
                        "-Xmx4g"
                        "-Xms4g"]
   :main ^:skip-aot bench}]

 :1.8 {:dependencies [[org.clojure/clojure "1.8.0-RC1"]]}

 ;; Examples
 :examples
 {:dependencies [[cheshire "5.5.0"]
                 [hiccup "1.0.5"]]}

 :websocket-echo-example
 [:examples
  {:source-paths ["examples/websocket-echo/src"]
   :resource-paths ["examples/websocket-echo/resources"]
   :main ^:skip-aot websocket-echo.core}]

 :component-chat-example
 [:examples
  {:source-paths ["examples/component-chat/src"]
   :resource-paths ["examples/component-chat/resources"]
   :main ^:skip-aot compchat.core}]

 :postal-chat-example
 [:examples
  {:source-paths ["examples/postal-chat/src/clj"
                  "examples/postal-chat/src/cljs"]
   :resource-paths ^:replace ["examples/postal-chat/resources"]
   :dependencies [[funcool/postal "0.2.0"]
                  [org.clojure/clojurescript "1.7.189"]]
   :main ^:skip-aot compchat.core}
  ]

 :debugging-example
 [:examples
  {:dependencies [[prone "0.8.2"]]
   :source-paths ["examples/debugging/src"]
   :resource-paths ["examples/debugging/resources"]
   :main ^:skip-aot debugging.core}]

 :interceptor-example
 [:examples
  {:source-paths ["examples/interceptor/src"]
   :resource-paths ["examples/interceptor/resources"]
   :main ^:skip-aot interceptor.core}]

 :website-example
 [:examples
  {:source-paths ["examples/website/src"]
   :resource-paths ["examples/website/resources"]
   :main ^:skip-aot website.core}]

 :website-ssl-example
 [:examples
  {:source-paths ["examples/website-ssl/src"]
   :resource-paths ["examples/website-ssl/resources"]
   :main ^:skip-aot website.core}]
 }

