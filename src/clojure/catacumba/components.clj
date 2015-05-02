(ns catacumba.components
  "A modular component implementation for catacumba server
  that plays in a stuartsierra/component lifecycle protocol."
  (:require [com.stuartsierra.component :as component]
            [futura.atomic :as atomic]
            [catacumba.core :as ct]
            [catacumba.impl.routing :as routing]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.server :refer [run-server]])
  (:import ratpack.handling.Chain
           ratpack.server.RatpackServer))

(defrecord Server [options server routes]
  component/Lifecycle
  (start [component]
    (let [handler (fn [^Chain chain]
                    (doseq [item (vals @routes)]
                      (reduce routing/attach-route chain item)))
          handler (with-meta handler {:type :ratpack-router})]
      (assoc component :server (run-server handler options))))

  (stop [component]
    (.stop ^RatpackServer server)
    (assoc component :server nil)))

(alter-meta! #'->Server assoc :private true)
(alter-meta! #'map->Server assoc :private true)

(defn catacumba-server
  "The catacumba server component constructor."
  [options]
  (map->Server {:options options
                :routes (atom {})
                :server nil}))

(defn extra-data
  "A chain handler that add extra data to the context
  and delegates the request processing to the next handler."
  [data]
  (fn [context]
    (ct/delegate context data)))

(defn assoc-routes!
  "Assoc routes to the running server. Executing this function
  causes the server reload."
  [server key routes]
  (let [server' (:server server)
        routes' (:routes server)]
    (swap! routes' assoc key routes)
    (.reload ^RatpackServer server')))
