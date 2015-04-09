(ns catacumba.impl.http)

(defrecord Response [body ^long status headers])

(alter-meta! #'->Response assoc :private true)
(alter-meta! #'map->Response assoc :private true)

(defn response
  "Create a response instance."
  ([body] (Response. body 200 {}))
  ([body status] (Response. body status {}))
  ([body status headers] (Response. body status headers)))
