(ns catacumba.impl.context
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:import ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handler
           ratpack.handling.Handlers
           ratpack.registry.Registry
           ratpack.registry.Registries
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.func.Action
           ratpack.func.Function
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           java.io.InputStream
           java.util.Map))

(defrecord ContextData [payload])
(alter-meta! #'->ContextData assoc :private true)
(alter-meta! #'map->ContextData assoc :private true)

(defn delegate
  "Delegate handling to the next handler in line.

  This function accept an additiona parameter for
  pass context parameters to the next handlers, and
  that can be obtained with `context-params`
  function."
  ([^Context context]
   (.next context))
  ([^Context context data]
   (let [reg (Registries/just (ContextData. data))]
     (.next context reg))))

(defn context-params
  "Get the current context params.

  The current params can be passed to the next
  handler using the `delegate` function. Is a simple
  way to communicate the handlers chain."
  [^Context context]
  (try
    (let [cdata (.get context ContextData)]
      (:payload cdata))
    (catch ratpack.registry.NotInRegistryException e
      {})))
