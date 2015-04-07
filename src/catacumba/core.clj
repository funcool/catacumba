(ns catacumba.core
  (:require [catacumba.impl :as impl]
            [catacumba.utils :as utils])
  (:import ratpack.server.RatpackServer
           ratpack.func.Action))

(defn run-server
  "Start and ratpack webserver to serve the given handler according
  to the supplied options:

  - `:port`    - the port to listen on (defaults to 5050)
  - `:threads` - the number of threads (default: number of cores * 2)
  - `:debug`   - start in development mode or not (default: true)

  Returns an Ratpack server instance."
  ([handler] (run-server handler {}))
  ([handler options]
   (let [^Action callback (utils/action #(impl/configure-server % handler options))
         ^RatpackServer server (RatpackServer/of callback)]
     (.start server)
    server)))
