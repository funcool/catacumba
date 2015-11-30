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

(ns catacumba.components
  "A modular component implementation for catacumba server
  that plays in a stuartsierra/component lifecycle protocol."
  (:require [com.stuartsierra.component :as component]
            [catacumba.core :as ct]
            [catacumba.impl.routing :as routing]
            [catacumba.impl.server :refer [run-server]])
  (:import ratpack.handling.Chain
           ratpack.server.RatpackServer))

(defrecord Server [options server routes]
  component/Lifecycle
  (start [component]
    (let [handler (fn [^Chain chain]
                    (doseq [item (vals @routes)]
                      (reduce routing/attach-route chain item)))
          handler (with-meta handler {:handler-type :catacumba/router})]
      (assoc component :server (run-server handler options))))

  (stop [component]
    (when server (.stop ^RatpackServer server))
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
    (ct/delegate data)))

(defn assoc-routes!
  "Assoc routes to the running server. Executing this function
  causes the server reload."
  [server key routes]
  (let [server' (:server server)
        routes' (:routes server)]
    (swap! routes' assoc key [(apply vector :scope routes)])
    (.reload ^RatpackServer server')))
