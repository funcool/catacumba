(ns catacumba.impl.context
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:require [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.handling.RequestOutcome
           ratpack.http.Request
           ratpack.http.Response
           ratpack.server.PublicAddress
           ratpack.registry.Registry
           ratpack.registry.Registries))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DefaultContext [^ratpack.http.Request request
                           ^ratpack.http.Response response])

(defrecord ContextData [payload])

(alter-meta! #'->DefaultContext assoc :private true)
(alter-meta! #'map->DefaultContext assoc :private true)
(alter-meta! #'->ContextData assoc :private true)
(alter-meta! #'map->ContextData assoc :private true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn context
  "A catacumba context constructor."
  [^Context context']
  (map->DefaultContext {:catacumba/context context'
                        :request (.getRequest context')
                        :response (.getResponse context')}))


(defn context-params
  "Get the current context params.

  The current params can be passed to the next
  handler using the `delegate` function. Is a simple
  way to communicate the handlers chain."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)]
    (try
      (let [cdata (.get ctx ContextData)]
        (:payload cdata))
      (catch ratpack.registry.NotInRegistryException e
        {}))))

(defn delegate
  "Delegate handling to the next handler in line.

  This function accept an additiona parameter for
  pass context parameters to the next handlers, and
  that can be obtained with `context-params`
  function."
  ([^DefaultContext context]
   (let [^Context ctx (:catacumba/context context)]
     (.next ctx)))
  ([^DefaultContext context data]
   (let [^Context ctx (:catacumba/context context)
         previous (context-params context)
         ^Registry reg (Registries/just (ContextData. (merge previous data)))]
     (.next ctx reg))))

(defn public-address
  "Get the current public address as URI instance.

  The default implementation uses a variety of strategies to
  attempt to provide the desired result most of the time.
  Information used includes:

  - Configured public address URI (optional)
  - X-Forwarded-Host header (if included in request)
  - X-Forwarded-Proto or X-Forwarded-Ssl headers (if included in request)
  - Absolute request URI (if included in request)
  - Host header (if included in request)
  - Service's bind address and scheme (http vs. https)"
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^PublicAddress addr (.get ctx PublicAddress)]
    (.getAddress addr ctx)))

(defn route-params
  "Return a hash-map with parameters extracted from
  routing patterns."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)]
    (into {} utils/keywordice-keys-t (.getPathTokens ctx))))

(defn on-close
  "Register a callback in the context that will be called
  when the connection with the client is closed."
  [^DefaultContext context callback]
  (let [^Context ctx (:catacumba/context context)]
    (.onClose ctx (helpers/action callback))))

(defn before-send
  "Register a callback in the context that will be called
  just before send the response to the client. Is a useful
  hook for set some additional cookies, headers or similar
  response transformations."
  [^DefaultContext context callback]
  (let [^Response response (:response context)]
    (.beforeSend response (helpers/action callback))))
