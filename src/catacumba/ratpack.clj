(ns catacumba.ratpack
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:require [catacumba.impl.ratpack :as impl])
  (:import ratpack.handling.Context
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
  (let [request (proto/get-request request)]
    (impl/get-headers request)))
