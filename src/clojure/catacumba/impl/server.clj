;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are met:
;;
;; * Redistributions of source code must retain the above copyright notice, this
;;   list of conditions and the following disclaimer.
;;
;; * Redistributions in binary form must reproduce the above copyright notice,
;;   this list of conditions and the following disclaimer in the documentation
;;   and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
;; AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
;; IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;; DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
;; FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
;; DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
;; SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;; CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
;; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.impl.server
  (:require [catacumba.helpers :as hp]
            [catacumba.impl.websocket :as websocket]
            [catacumba.impl.handlers :as handlers]
            [catacumba.helpers :as hp]
            [clojure.java.io :as io]
            [environ.core :refer [env]])
  (:import ratpack.server.RatpackServer
           ratpack.server.ServerConfig
           ratpack.server.BaseDir
           ratpack.server.RatpackServerSpec
           ratpack.handling.HandlerDecorator
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
  [factory ^RatpackServerSpec spec]
  (.handlers spec ^Action (hp/fn->action factory)))

(defmethod setup-handler :default
  [handler ^RatpackServerSpec spec]
  (.handler spec (reify Function
                   (apply [_ _]
                     (handlers/adapter handler)))))

(defn- bootstrap-registry
  "A bootstrap server hook for setup initial
  registry entries and execute a user provided
  hook for do the same thing."
  [^RegistrySpec spec {:keys [setup debug decorators]}]
  (when (fn? setup)
    (setup spec))
  (when (seq decorators)
    (doseq [item decorators]
      (.add spec (HandlerDecorator/prepend (handlers/adapter item))))))

(defn- build-ssl-context
  [{:keys [keystore-secret keystore-path]}]
  (when (and keystore-secret keystore-path)
    (let [keystore (io/resource keystore-path)]
      (SSLContexts/sslContext keystore keystore-secret))))

(defn- build-server-config
  "Given user specified options, return a `ServerConfig` instance."
  [{:keys [port debug threads basedir public-address max-body-size]
    :or {debug false}
    :as options}]
  (let [port (or (:catacumba-port env) port ServerConfig/DEFAULT_PORT)
        threads (or (:catacumba-threads env) threads ServerConfig/DEFAULT_THREADS)
        basedir (or (:catacumba-basedir env) basedir)
        debug (or (:catacumba-debug env) debug)
        sslcontext (build-ssl-context options)
        config (ServerConfig/builder)]
    (if (string? basedir)
      (.baseDir config ^Path (hp/str->path basedir))
      (hp/with-ignore-exception IllegalStateException
        (let [^Path path (BaseDir/find ".catacumba")]
          (.baseDir config path))))
    (when sslcontext (.ssl config sslcontext))
    (when public-address (.publicAddress config (java.net.URI. public-address)))
    (when max-body-size (.maxContentLength config max-body-size))
    (.port config port)
    (.threads config threads)
    (.development config (boolean debug))
    (.build config)))

(defn- configure-server
  "The ratpack server configuration callback."
  [^RatpackServerSpec spec handler options]
  (.serverConfig spec ^ServerConfig (build-server-config options))
  (.registryOf spec (hp/fn->action #(bootstrap-registry % options)))
  (setup-handler handler spec))

(defn run-server
  "Start and ratpack webserver to serve the given handler according
  to the supplied options:

  - `:port`: the port to listen on (defaults to 5050).
  - `:threads`: the number of threads (default: number of cores * 2).
  - `:debug`: start in development mode or not (default: `true`).
  - `:setup`: callback for add additional entries in ratpack registry.
  - `:basedir`: the application base directory. Used mainly for resolve relative paths and assets.
  - `:decorators`: a list of handlers that will be chained at the first of the request pipeline.
  - `:public-address`: force a specific public address (default: `nil`).
  - `:max-body-size`: set the maximum size of the body (default: 1048576 bytes (1mb))

  Additional notes:

  - The `:basedir` is used mainly to resolve relative paths for assets. When you set
    no basedir it first try to find a .catacumba file in the root of you classpath if
    it cannot find that it will run without a basedir
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
   (let [^Action callback (hp/fn->action #(configure-server % handler options))
         ^RatpackServer server (RatpackServer/of callback)]
     (.start server)
    server)))
