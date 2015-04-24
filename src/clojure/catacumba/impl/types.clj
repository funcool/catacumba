(ns catacumba.impl.types
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Request
           ratpack.http.Response))

(defrecord DefaultContext [^ratpack.http.Request request
                           ^ratpack.http.Response response])

(alter-meta! #'->DefaultContext assoc :private true)
(alter-meta! #'map->DefaultContext assoc :private true)

(defn ->context
  "A catacumba context constructor."
  [^Context context]
  (map->DefaultContext {:catacumba/context context
                        :request (.getRequest context)
                        :response (.getResponse context)}))
