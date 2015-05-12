(ns catacumba.handlers.core
  (:require [catacumba.impl.context :as context]
            [catacumba.impl.handlers :as handlers])
  (:import ratpack.http.Request))

(defn hydrate-context
  "Populates the context with request related
  properties."
  {:no-doc true}
  [context]
  (let [^Request request (:request context)]
    (assoc context
           :path (str "/" (.. request getPath))
           :query-string (.. request getQuery)
           :method (keyword (.. request getMethod getName toLowerCase))
           :headers (handlers/get-headers request))))

(defn basic-request
  "A chain handler that populates the context
  with a basic request related properties."
  [context]
  (->> (hydrate-context context)
       (context/delegate context)))
