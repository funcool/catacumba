;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are met:
;;
;; * Redistributions of source code must retain the above copyright notice, this
;;   list of conditions and the following disclaimer.
;;
;; * Redistributions in binary form must reproduce the above copyright notice,
;;   this list of conditions and the following disclaimer in the documentation
;;   and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
;; AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
;; IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;; DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
;; FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
;; DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
;; SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;; CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
;; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.impl.handlers
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io]
            [manifold.stream :as ms]
            [manifold.deferred :as md]
            [promissum.core :as p]
            [catacumba.stream :as stream]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as ch]
            [catacumba.impl.http :as http])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.ResponseMetaData
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.util.MultiValueMap
           ratpack.exec.Downstream
           ratpack.exec.Promise
           ratpack.exec.Blocking
           catacumba.impl.context.DefaultContext
           org.reactivestreams.Publisher
           java.util.concurrent.CompletableFuture
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           io.netty.handler.codec.http.Cookie
           java.io.InputStream
           java.io.BufferedReader
           java.io.InputStreamReader
           java.io.BufferedInputStream
           java.util.Map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISend
  "A low level send abstraction."
  (send [data ctx] "Send data."))

(defprotocol IHandlerResponse
  (handle-response [_ context] "Handle the ratpack handler response."))

(defprotocol IHeaders
  (get-headers* [_] "Get headers.")
  (set-headers* [_ headers] "Set the headers."))

(defprotocol ICookies
  (get-cookies* [_] "Get cookies.")
  (set-cookies* [_ cookies] "Set cookies."))

(defprotocol IResponse
  (set-status* [_ status] "Set the status code."))

(defprotocol IRequest
  (get-body* [_] "Get the body."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol IHandlerResponse
  String
  (handle-response [data ^DefaultContext context]
    (send data (:catacumba/context context)))

  clojure.lang.IPersistentMap
  (handle-response [data ^DefaultContext context]
    (let [{:keys [status headers body]} data]
      (when status (set-status* context status))
      (when headers (set-headers* context headers))
      (send body (:catacumba/context context))))

  catacumba.impl.http.Response
  (handle-response [data ^DefaultContext context]
    (set-status* context (:status data))
    (set-headers* context (:headers data))
    (send (:body data) (:catacumba/context context)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (handle-response [data ^DefaultContext context]
    (set-status* context 200)
    (send data (:catacumba/context context)))

  manifold.stream.default.Stream
  (handle-response [data ^DefaultContext context]
    (set-status* context 200)
    (send data (:catacumba/context context)))

  Publisher
  (handle-response [data ^DefaultContext context]
    (set-status* context 200)
    (send data (:catacumba/context context)))

  CompletableFuture
  (handle-response [data ^DefaultContext context]
    (set-status* context 200)
    (send data (:catacumba/context context))))

(extend-protocol ISend
  String
  (send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (.send response data)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (send [data ^Context ctx]
    (-> (stream/publisher data)
        (send ctx)))

  manifold.stream.default.Stream
  (send [data ^Context ctx]
    (-> (stream/publisher data)
        (send ctx)))

  manifold.deferred.IDeferred
  (send [data ^Context ctx]
    (-> (stream/publisher data)
        (send ctx)))

  CompletableFuture
  (send [future' ^Context ctx]
    (-> (ch/promise (fn [resolve] (resolve future')))
        (ch/then #(send % ctx))))

  Publisher
  (send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (->> (stream/publisher data)
           (stream/transform (map ch/bytebuffer))
           (stream/bind-exec)
           (.sendStream response))))

  ;; TODO: reimplement this as chunked stream instread of
  ;; read all data in memory. The current approach is slightly
  ;; awfull because it reads all inputstream firstly in a memory.
  InputStream
  (send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)
          ^Promise prom (ch/blocking
                         (let [^bytes buffer (byte-array 1024)
                               ^ByteBuf buf (Unpooled/buffer (.available data))]
                           (loop [index 0]
                             (let [readed (.read data buffer 0 1024)]
                               (when-not (= readed -1)
                                 (.writeBytes buf buffer 0 readed)
                                 (recur (+ index readed)))))
                           buf))]
      (ch/then prom (fn [buff]
                      (.send response buff))))))

(extend-protocol IResponse
  DefaultContext
  (set-status* [^DefaultContext ctx ^long status]
    (set-status* (:response ctx) status))

  ResponseMetaData
  (set-status* [^ResponseMetaData response ^long status]
    (.status response status)))

(extend-protocol IRequest
  DefaultContext
  (get-body* [^DefaultContext context]
    (get context :body))

  Request
  (get-body* [^Request request]
    (Blocking/on (.getBody request))))

(extend-protocol IHeaders
  DefaultContext
  (get-headers* [^DefaultContext ctx]
    (get-headers* ^Request (:request ctx)))
  (set-headers* [^DefaultContext ctx headers]
    (set-headers* ^ResponseMetaData (:response ctx) headers))

  Request
  (get-headers* [^Request request]
    (let [^Headers headers (.getHeaders request)
          ^MultiValueMap headers (.asMultiValueMap headers)]
      (persistent!
       (reduce (fn [acc ^String key]
                 (let [values (.getAll headers key)
                       key (.toLowerCase key)]
                   (reduce #(utils/assoc-conj! %1 key %2) acc values)))
               (transient {})
               (.keySet headers)))))
  (set-headers* [_ _]
    (throw (UnsupportedOperationException.)))

  ResponseMetaData
  (get-headers* [_]
    (throw (UnsupportedOperationException.)))

  (set-headers* [^ResponseMetaData response headers]
    (let [^MutableHeaders headersmap (.getHeaders response)]
      (loop [headers headers]
        (when-let [[key vals] (first headers)]
          (.set headersmap (name key) vals)
          (recur (rest headers)))))))

(defn- cookie->map
  [cookie]
  {:path (.path cookie)
   :value (.value cookie)
   :domain (.domain cookie)
   :http-only (.isHttpOnly cookie)
   :secure (.isSecure cookie)
   :max-age (.maxAge cookie)})

(extend-protocol ICookies
  DefaultContext
  (get-cookies* [^DefaultContext ctx]
    (get-cookies* ^Request (:request ctx)))
  (set-cookies* [^DefaultContext ctx cookies]
    (set-cookies* ^Response (:response ctx) cookies))

  Request
  (get-cookies* [^Request request]
    (persistent!
     (reduce (fn [acc cookie]
               (let [name (keyword (.name cookie))]
                 (assoc! acc name (cookie->map cookie))))
             (transient {})
             (into [] (.getCookies request)))))

  (set-cookies* [_ _]
    (throw (UnsupportedOperationException.)))

  ResponseMetaData
  (get-cookies* [_]
    (throw (UnsupportedOperationException.)))

  (set-cookies* [^ResponseMetaData response cookies]
    (loop [cookies (into [] cookies)]
      (when-let [[cookiename cookiedata] (first cookies)]
        (let [cookie (.cookie response (name cookiename) "")]
          (reduce (fn [_ [k v]]
                    (case k
                      :path (.setPath cookie v)
                      :domain (.setDomain cookie v)
                      :secure (.setSecure cookie v)
                      :http-only (.setHttpOnly cookie v)
                      :max-age (.setMaxAge cookie v)
                      :value (.setValue cookie v)))
                  nil
                  (into [] cookiedata))
          (recur (rest cookies)))))))

(extend-protocol io/IOFactory
  TypedData
  (make-reader [d opts]
    (BufferedReader. (InputStreamReader. ^InputStream (.getInputStream d)
                                         ^String (:encoding opts "UTF-8"))))
  (make-writer [d opts]
    (throw (UnsupportedOperationException. "Cannot open as Reader.")))
  (make-input-stream [d opts]
    (BufferedInputStream. (.getInputStream d)))
  (make-output-stream [d opts]
    (throw (UnsupportedOperationException. "Cannot open as Reader.")))

  DefaultContext
  (make-reader [ctx opts]
    (io/make-reader (get-body* ctx) opts))
  (make-writer [ctx opts]
    (io/make-writer (get-body* ctx) opts))
  (make-input-stream [ctx opts]
    (io/make-input-stream (get-body* ctx) opts))
  (make-output-stream [ctx opts]
    (io/make-output-stream (get-body* ctx) opts))

  Request
  (make-reader [req opts]
    (io/make-reader (get-body* req) opts))
  (make-writer [req opts]
    (io/make-writer (get-body* req) opts))
  (make-input-stream [req opts]
    (io/make-input-stream (get-body* req) opts))
  (make-output-stream [req opts]
    (io/make-output-stream (get-body* req) opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-body
  "Helper for obtain a object that represents a request
  body. The returned object implements the IOFactory
  protocol that allows use it with `clojure.java.io`
  functions and `slurp`."
  [context]
  (get-body* context))

(defn get-headers
  "Get request headers.

  This is a polymorphic function and accepts
  Context instances as request."
  [request]
  (get-headers* request))

(defn set-headers!
  "Set response headers.

  This is a polymorphic function and accepts
  Context instances as response."
  [response headers]
  (set-headers* response headers))

(defn set-status!
  "Set response status code."
  [response status]
  (set-status* response status))

(defn get-cookies
  "Get the incoming cookies.

  This is a polymorphic function and accepts
  Context instances as request."
  [request]
  (get-cookies* request))

(defn set-cookies!
  "Set the outgoing cookies.

      (set-cookies! ctx {:cookiename {:value \"value\"}})

  As well as setting the value of the cookie,
  you can also set additional attributes:

  - `:domain` - restrict the cookie to a specific domain
  - `:path` - restrict the cookie to a specific path
  - `:secure` - restrict the cookie to HTTPS URLs if true
  - `:http-only` - restrict the cookie to HTTP if true
                   (not accessible via e.g. JavaScript)
  - `:max-age` - the number of seconds until the cookie expires

  As you can observe is almost identical hash map structure
  as used in the ring especification.

  This is a polymorphic function and accepts
  Context instances as response."
  [response cookies]
  (set-cookies* response cookies))

(defmulti send!
  "Send data to the client."
  (fn [response data] (class response)))

(defmethod send! DefaultContext
  [^DefaultContext context data]
  (send data (:catacumba/context context)))

(defmulti adapter
  "A polymorphic function for adapt catacumba
  handlers to ratpack compatible handlers.

  The dispatch is done by `:handler-type` key in the
  metadata found on the given var or anonymous
  handler."
  (fn [handler & args]
    (let [metadata (meta handler)]
      (:handler-type metadata)))
  :default :catacumba/default)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod adapter :catacumba/default
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            context-params (ctx/context-params context)
            route-params (ctx/route-params context)
            bodyp (.. ctx getRequest getBody)]
        (ch/then bodyp (fn [^TypedData body]
                         (let [context' (-> (merge context context-params)
                                            (assoc :route-params route-params)
                                            (assoc :body body))
                               response (handler context')]
                           (when (satisfies? IHandlerResponse response)
                             (handle-response response context)))))))))


(defmethod adapter :catacumba/cps
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            context-params (ctx/context-params context)
            route-params (ctx/route-params context)
            context (-> (merge context context-params)
                        (assoc :route-params route-params))]
        (-> (ch/promise (fn [resolve] (handler context #(resolve %))))
            (ch/then #(handle-response % context)))))))

(defn build-request
  [^Request request]
   (let [local-address (.getLocalAddress request)
         remote-address (.getRemoteAddress request)
         body (Blocking/on (.getBody request))]
     {:server-port (.getPort local-address)
      :server-name (.getHostText local-address)
      :remote-addr (.getHostText remote-address)
      :uri (str "/" (.getPath request))
      :query-string (.getQuery request)
      :scheme :http
      :request-method (keyword (.. request getMethod getName toLowerCase))
      :headers (get-headers* request)
      :content-type (.. body getContentType getType)
      :content-length (Integer/parseInt (.. request getHeaders (get "Content-Length")))
      :character-encoding (.. body getContentType (getCharset "utf-8"))
      :body (.. body getInputStream)}))

(defmethod adapter :catacumba/ring
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            p (ch/blocking
               (let [request (build-request (:request context))]
                 (handler request)))]
        (ch/then p (fn [response]
                     (when (satisfies? IHandlerResponse response)
                       (handle-response response context))))))))
