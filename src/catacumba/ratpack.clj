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

(defmulti attach-route
  (fn [chain [method & _]] method))

(defmethod attach-route :assets
  [^Chain chain [_ ^String path & indexes]]
  (let [indexes (into-array String indexes)]
    (.assets chain path indexes)))

(defmethod attach-route :prefix
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.prefix chain path ^Action (utils/action callback))))

(defmethod attach-route :default
  [^Chain chain [method ^String path & handlers]]
  (let [^java.util.List handlers (mapv impl/ratpack-adapter handlers)]
    (condp = method
      :get (.get chain path (Handlers/chain handlers))
      :post (.post chain path (Handlers/chain handlers))
      :put (.put chain path (Handlers/chain handlers))
      :patch (.patch chain path (Handlers/chain handlers))
      :delete (.delete chain path (Handlers/chain handlers))
      :all (.handler chain path (Handlers/chain handlers)))))

(defn routes
  "Is a high order function that access a routes vector
  as argument and return a ratpack router type handler."
  [routes]
  (with-meta
    (fn [chain] (reduce attach-route chain routes))
    {:type :ratpack-router}))

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
