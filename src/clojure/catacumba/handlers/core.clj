(ns catacumba.handlers.core
  (:require [catacumba.core :as ct])
  (:import ratpack.http.Request))

(defn basic-request
  "A chain handler that populates the context
  with a basic request related properties."
  [context]
  (let [^Request request (:request context)]
    (->> {:path (str "/" (.. request getPath))
          :query-string (.. request getQuery)
          :method (keyword (.. request getMethod getName toLowerCase))
          :headers (ct/get-headers request)}
         (ct/delegate context))))
