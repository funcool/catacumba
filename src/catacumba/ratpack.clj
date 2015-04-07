(ns catacumba.ratpack
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:refer-clojure :exclude [next])
  (:require [catacumba.impl.ratpack :as impl]
            [catacumba.utils :as utils])
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

(defn send!
  "Send data to the client."
  [response data]
  (let [response (impl/get-response response)]
    (impl/send data response)))

(defn get-headers
  "Get headers from request."
  [request]
  (let [request (impl/get-request request)]
    (impl/get-headers request)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- chain-handler
  [handler]
  (fn [^Context context]
    (let [params (into {} (.getPathTokens context))]
      (handler context params))))

(defn route
  [^Chain chain routes]
  (let [make-handler (comp impl/ratpack-adapter chain-handler)]
    (reduce (fn [^Chain chain [method path & handlers]]
              (condp = method
                :get (.get chain path (Handlers/chain (mapv make-handler handlers)))
                :post (.post chain path (Handlers/chain (mapv make-handler handlers)))
                :put (.put chain path (Handlers/chain (mapv make-handler handlers)))
                :patch (.patch chain path (Handlers/chain (mapv make-handler handlers)))
                :delete (.delete chain path (Handlers/chain (mapv make-handler handlers)))
                :all (.handler chain path (Handlers/chain (mapv make-handler handlers)))
                :prefix (.prefix chain path (utils/action #(route % handlers)))))
            chain
            routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers Communication
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ContextData [payload])

(alter-meta! #'->ContextData assoc :private true)
(alter-meta! #'map->ContextData assoc :private true)

(defn delegate
  "Delegate handling to the next handler in line."
  ([^Context context]
   (.next context))
  ([^Context context data]
   (let [reg (Registries/just (ContextData. data))]
     (.next context reg))))

(defn context-data
  "Get current context data."
  [^Context context]
  (try
    (let [cdata (.get context ContextData)]
      (:payload cdata))
    (catch ratpack.registry.NotInRegistryException e
      {})))
