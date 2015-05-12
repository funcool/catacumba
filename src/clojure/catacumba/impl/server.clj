(ns catacumba.impl.server
  (:require [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.websocket :as websocket]
            [catacumba.impl.handlers :as handlers]
            [clojure.java.io :as io]
            [environ.core :refer [env]])
  (:import ratpack.server.RatpackServer
           ratpack.server.ServerConfig
           ratpack.server.RatpackServerSpec
           ratpack.registry.RegistrySpec
           ratpack.ssl.SSLContexts
           ratpack.func.Action
           ratpack.func.Function
           java.nio.file.Path))

(defmulti setup-handler
  "A polymorphic function for setup the handler
  to the reatpack server instance builder."
  (fn [handler spec]
    (let [metadata (meta handler)]
      (:handler-type metadata))))

(defmethod setup-handler :catacumba/router
  [handler ^RatpackServerSpec spec]
  (.handlers spec ^Action (helpers/action handler)))

(defmethod setup-handler :default
  [handler ^RatpackServerSpec spec]
  (letfn [(rhandler [_] (handlers/adapter handler))]
    (.handler spec ^Function (helpers/function rhandler))))

(defn- bootstrap-registry
  "A bootstrap server hook for setup initial
  registry entries and execute a user provided
  hook for do the same thing."
  [^RegistrySpec spec {:keys [setup debug]}]
  (when (fn? setup)
    (setup spec)))

(defn- build-ssl-context
  [{:keys [path secret]}]
  (when (and path secret)
    (let [keystore (io/resource path)]
      (SSLContexts/sslContext keystore secret))))

(defn- build-server-config
  "Given user specified options, return a `ServerConfig` instance."
  [{:keys [port debug threads basedir keystore public-address max-body-size] :or {debug true}}]
  (let [port (or (:catacumba-port env) port ServerConfig/DEFAULT_PORT)
        threads (or (:catacumba-threads env) threads ServerConfig/DEFAULT_THREADS)
        basedir (or (:catacumba-basedir env) basedir)
        debug (or (:catacumba-debug env) debug)
        sslcontext (build-ssl-context keystore)
        config (if (string? basedir)
                 (ServerConfig/baseDir ^Path (utils/str->path basedir))
                 (ServerConfig/noBaseDir))]
    (when sslcontext (.ssl config sslcontext))
    (when public-address (.publicAddress config (java.net.URI. public-address)))
    (when max-body-size (.maxContentLength config max-body-size))
    (.port config port)
    (.threads config threads)
    (.development config (boolean debug))
    (.build config)))

(defn- configure-server
  "The ratpack server configuration callback."
  [^RatpackServerSpec spec handler {:keys [setup] :as options}]
  (.serverConfig spec ^ServerConfig (build-server-config options))
  (.registryOf spec (helpers/action #(bootstrap-registry % options)))
  (setup-handler handler spec))

(defn run-server
  "Start and ratpack webserver to serve the given handler according
  to the supplied options:

  - `:port`: the port to listen on (defaults to 5050).
  - `:threads`: the number of threads (default: number of cores * 2).
  - `:debug`: start in development mode or not (default: `true`).
  - `:setup`: callback for add additional entries in ratpack registry.
  - `:basedir`: the application base directory. Used mainly for resolve relative paths and assets.
  - `:public-address`: force a specific public address (default: `nil`).
  - `:max-body-size`: set the maximum size of the body (default: 1048576 bytes (1mb))

  Additional notes:

  - The `:basedir` is used mainly for resolve relative paths for assets.
  - With `:publicaddress` you can force one specific public address, in case contrary it
    will be discovered using a variety of different strategies explained in the
    `public-address` function docstring.
  - You can set all that parameters using environment variables or jvm system
    properties. For example, you can use `CATACUMBA_BASEDIR` environ variable or
    `catacumba.basedir` jvm system property for overwrite the `:basedir` parameter
    value.

  This function does not blocks and you can execute it in a repl without
  problems. It uses jvm not daemon threads for avoid shutdown the jvm.
  "
  ([handler] (run-server handler {}))
  ([handler options]
   (let [^Action callback (helpers/action #(configure-server % handler options))
         ^RatpackServer server (RatpackServer/of callback)]
     (.start server)
    server)))
