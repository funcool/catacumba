{:dev
 {:global-vars {*warn-on-reflection* false}
  :source-paths ["src"]
  :codeina {:sources ["src/clojure"]
            :exclude [catacumba.impl.context
                      catacumba.impl.helpers
                      catacumba.impl.parse
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
                      catacumba.impl.streams
                      catacumba.impl.websocket
                      catacumba.impl.sse
                      catacumba.impl.types
                      catacumba.handlers.core
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
            :src-uri-prefix "#L"}
  :plugins [[funcool/codeina "0.2.0"]
            [lein-ancient "0.6.7" :exclusions [org.clojure/tools.reader]]]
  :dependencies [[clj-http "1.1.2"]
                 [aleph "0.4.0" :exclusions [io.netty/netty-all]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring/ring-core "1.4.0"
                  :exclusions [javax.servlet/servlet-api clj-time org.clojure/clojure]]]}
 ;; Examples

 :websocket-example
 {:source-paths ["examples/echo-websocket/src"]
  :resource-paths ["examples/echo-websocket/resources"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]]
  :main ^:skip-aot echo-websocket.core}}

