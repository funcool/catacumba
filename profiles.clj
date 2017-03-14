{:dev
 {:global-vars {*warn-on-reflection* false}
  :aliases {"test-all" ["with-profile" "dev,1.9:dev,1.7:dev" "test"]}
  :source-paths ["dev"]
  :dependencies [[clj-http "3.4.1" :exclusions [org.clojure/tools.reader]]
                 [aleph "0.4.1" :exclusions [primitive-math io.netty/netty-all]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring/ring-core "1.4.0" :exclusions [javax.servlet/servlet-api
                                                      org.clojure/tools.reader
                                                      org.clojure/clojure
                                                      clj-time]]]
  :plugins [[lein-ancient "0.6.10"]
            [funcool/codeina "0.4.0"]]
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
                      catacumba.impl.helpers
                      catacumba.impl.sse]
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

 :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]]}
 :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}

 ;; Examples
 :examples
 {:dependencies [[cheshire "5.5.0"]
                 [hiccup "1.0.5"]]}

 :websocket-echo-example
 [:examples
  {:source-paths ["examples/websocket-echo/src"]
   :resource-paths ["examples/websocket-echo/resources"]
   :main ^:skip-aot websocket-echo.core}]

 :sse-chat-example
 [:examples
  {:source-paths ["examples/sse-chat/src"]
   :resource-paths ["examples/sse-chat/resources"]
   :jvm-opts ^:replace ["-XX:+AggressiveOpts"
                        "-XX:+UseG1GC"
                        "-Xmx100m"
                        "-Xms100m"]
   :main ^:skip-aot compchat.core}]


 :debugging-example
 [:examples
  {:dependencies [[funcool/catacumba-prone "0.4.0"]]
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

