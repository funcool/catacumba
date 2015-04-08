(ns catacumba.impl
  (:require [catacumba.utils :as utils]
            [catacumba.impl.ring :as ring]
            [catacumba.impl.ratpack :as ratpack]
            [environ.core :refer [env]])
  (:import ratpack.server.RatpackServer
           ratpack.server.ServerConfig
           ratpack.server.RatpackServerSpec
           ratpack.registry.RegistrySpec
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
           java.nio.file.Path
           java.io.InputStream
           java.util.Map))

(defmulti ^:private setup-handler
  "A polymorphic ratpack handler constructor."
  (fn [handler spec] (:type (meta handler)))
  :default :ratpack)

(defmethod setup-handler :ratpack
  [handler ^RatpackServerSpec spec]
  (letfn [(rhandler [_]
            (ratpack/ratpack-adapter handler))]
    (.handler spec ^Function (utils/function rhandler))))

(defmethod setup-handler :ratpack-router
  [handler ^RatpackServerSpec spec]
  (.handlers spec (utils/action handler)))

(defmethod setup-handler :ring
  [handler spec]
  (let [handler (-> (ring/ring-adapter handler)
                    (with-meta {:type :ratpack}))]
    (setup-handler handler spec)))

(defn- bootstrap-registry
  [^RegistrySpec registryspec setup]
  (when (fn? setup)
    (setup registryspec)))

(defn- build-server-config
  "Given user specified options, return a `ServerConfig` instance."
  [{:keys [port debug threads basedir] :or {debug true}}]
  (let [port (or (:catacumba-port env) port ServerConfig/DEFAULT_PORT)
        threads (or (:catacumba-threads env) threads ServerConfig/DEFAULT_THREADS)
        basedir (or (:catacumba-basedir env) basedir)
        debug (or (:catacumba-debug env) debug)
        config (if (string? basedir)
                 (ServerConfig/baseDir ^Path (utils/str->path basedir))
                 (ServerConfig/findBaseDirProps "catacumba.properties"))]
    (.port config port)
    (.threads config threads)
    (.development config (boolean debug))
    (.build config)))

(defn configure-server
  "The ratpack server configuration callback."
  [^RatpackServerSpec spec handler {:keys [setup] :as options}]
  (.serverConfig spec ^ServerConfig (build-server-config options))
  (.registryOf spec (utils/action #(bootstrap-registry % setup)))
  (setup-handler handler spec))
