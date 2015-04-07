(ns catacumba.impl
  (:require [catacumba.utils :as utils]
            [catacumba.impl.ring :as ring]
            [catacumba.impl.ratpack :as ratpack])
  (:import ratpack.server.RatpackServer
           ratpack.server.ServerConfig
           ratpack.server.RatpackServerSpec
           ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.stream.Streams
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.func.Action
           ratpack.func.Function
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           java.io.InputStream
           java.util.Map))

(defmulti setup-handler
  "A polymorphic ratpack handler constructor."
  (fn [handler spec] (:type (meta handler)))
  :default :ratpack)

(defmethod setup-handler :ratpack
  [handler ^RatpackServerSpec spec]
  (letfn [(rhandler [_]
            (reify Handler
              (^void handle [_ ^Context context]
                (let [rsp (handler context)]
                  (when (satisfies? ratpack/IHandlerResponse rsp)
                    (ratpack/handle-response rsp context))))))]
    (.handler spec ^Function (utils/function rhandler))))

(defmethod setup-handler :ratpack-router
  [handler ^RatpackServerSpec spec]
  (.handlers spec (utils/action handler)))

(defmethod setup-handler :ring
  [handler spec]
  (let [handler (-> (ring/ring-adapter handler)
                    (with-meta {:type :ratpack}))]
    (setup-handler handler spec)))

(defn configure-server
  "The ratpack server configuration callback."
  [^RatpackServerSpec spec handler {:keys [port debug threads]
                                    :or {port 5050 debug true}
                                    :as options}]
  (let [config (-> (ServerConfig/embedded)
                   (.port port)
                   (.development debug))]
    (when threads
      (.threads config threads))
    (.serverConfig spec (.build config))
    (setup-handler handler spec)))
