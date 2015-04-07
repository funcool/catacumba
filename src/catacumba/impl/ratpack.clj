(ns catacumba.impl.ratpack
  (:refer-clojure :exclude [send])
  (:require [catacumba.utils :as utils])
  (:import ratpack.server.RatpackServer
           ratpack.server.ServerConfig
           ratpack.server.RatpackServerSpec
           ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.stream.Streams
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-response
  [^Context ctx]
  (.getResponse ctx))

(defn get-request
  [^Context ctx]
  (.getRequest ctx))

(defn get-headers
  [request]
  (let [^Request request (if (instance? Request request)
                           request
                           (get-request request))
        ^Headers headers (.getHeaders request)
        ^Map headers (.asMultiValueMap headers)]
    (into {} utils/lowercase-keys-t headers)))

(defn set-headers
  [response headers]
  (let [^Response response (if (instance? Response response)
                             response
                             (get-response response))
        ^MutableHeaders headersmap (.getHeaders response)]
    (loop [headers headers]
      (when-let [[^String key vals] (first headers)]
        (.set headersmap key vals)
        (recur (rest headers))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IResponse
  (send [data response] "Send data."))

(defprotocol IHandlerResponse
  (handle-response [_ context] "Handle the ratpack handler response."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(extend-protocol IHandlerResponse
  String
  (handle-response [data ^Context context]
    (let [response (get-response context)]
      (send data response))))

  ;; clojure.lang.IPersistentVector
  ;; (handle-response [rsp ^Context context]
  ;;   (let [^Response response (.getResponse context)]
  ;;     (if (= (count rsp) 2)
  ;;       (let [[data status] rsp]
  ;;         (.status response status)
  ;;         (handle-response data context))
  ;;       (let [[data status headers]]
  ;;         (.status response status)

(extend-protocol IResponse
  String
  (send [data ^Response response]
    (.send response data)))
