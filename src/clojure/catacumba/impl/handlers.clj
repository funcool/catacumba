(ns catacumba.impl.handlers
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io]
            [futura.stream :as stream]
            [futura.promise :as p]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.http :as http])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.util.MultiValueMap
           catacumba.impl.context.DefaultContext
           org.reactivestreams.Publisher
           java.util.concurrent.CompletableFuture
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           java.io.InputStream
           java.io.BufferedReader
           java.io.InputStreamReader
           java.io.BufferedInputStream
           java.util.Map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISend
  (send [data response] "Send data."))

(defprotocol IHandlerResponse
  (handle-response [_ context] "Handle the ratpack handler response."))

(defprotocol IHeaders
  (get-headers* [_] "Get headers.")
  (set-headers* [_ headers] "Set the headers."))

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
    (let [response (:response context)]
      (send data response)))

  clojure.lang.IPersistentMap
  (handle-response [data ^DefaultContext context]
    (let [^Response response (:response context)
          {:keys [status headers body]} data]
      (when status
        (.status response ^long status))
      (when headers
        (set-headers* response headers))
      (send body response)))

  catacumba.impl.http.Response
  (handle-response [data ^DefaultContext context]
    (let [^Response response (:response context)]
      (.status response ^long (:status data))
      (set-headers* response (:headers data))
      (send (:body data) response)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (handle-response [data ^DefaultContext context]
    (let [^Response response (:response context)]
      (.status response 200)
      (send data response)))

  Publisher
  (handle-response [data ^DefaultContext context]
    (let [^Response response (:response context)]
      (.status response 200)
      (send data response)))

  futura.promise.Promise
  (handle-response [data ^DefaultContext context]
    (let [^Response response (:response context)]
      (.status response 200)
      (send data response)))

  CompletableFuture
  (handle-response [data ^DefaultContext context]
    (handle-response (p/promise data) context)))

(extend-protocol ISend
  String
  (send [data ^Response response]
    (.send response data))

  clojure.core.async.impl.channels.ManyToManyChannel
  (send [data ^Response response]
    (-> (stream/publisher data)
        (send response)))

  futura.promise.Promise
  (send [data ^Response response]
    (-> (stream/publisher data)
        (send response)))

  CompletableFuture
  (send [data ^Response response]
    (-> (stream/publisher data)
        (send response)))

  Publisher
  (send [data ^Response response]
    (->> (stream/publisher (map helpers/bytebuffer) data)
         (.sendStream response)))

  InputStream
  (send [data ^Response response]
    (let [^bytes buffer (byte-array 1024)
          ^ByteBuf buf (Unpooled/buffer (.available data))]
      (loop [index 0]
        (let [readed (.read data buffer 0 1024)]
          (when-not (= readed -1)
            (.writeBytes buf buffer 0 readed)
            (recur (+ index readed)))))
      (.send response buf))))

(extend-protocol IResponse
  DefaultContext
  (set-status* [^DefaultContext ctx ^long status]
    (set-status* (:response ctx) status))

  Response
  (set-status* [^Response response ^long status]
    (.status response status)))

(extend-protocol IRequest
  DefaultContext
  (get-body* [^DefaultContext context]
    (get-body* ^Request (:request context)))

  Request
  (get-body* [^Request request]
    (.getBody request)))

(extend-protocol IHeaders
  DefaultContext
  (get-headers* [^DefaultContext ctx]
    (get-headers* ^Request (:request ctx)))
  (set-headers* [^DefaultContext ctx headers]
    (set-headers* ^Response (:response ctx) headers))

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

  Response
  (get-headers* [_]
    (throw (UnsupportedOperationException.)))

  (set-headers* [^Response response headers]
    (let [^MutableHeaders headersmap (.getHeaders response)]
      (loop [headers headers]
        (when-let [[key vals] (first headers)]
          (.set headersmap (name key) vals)
          (recur (rest headers)))))))

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

  Context
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

(defn build-request
  [^Request request]
  (let [local-address (.getLocalAddress request)
        remote-address (.getRemoteAddress request)]
    {:server-port (.getPort local-address)
     :server-name (.getHostText local-address)
     :remote-addr (.getHostText remote-address)
     :uri (str "/" (.getPath request))
     :query-string (.getQuery request)
     :scheme :http
     :request-method (keyword (.. request getMethod getName toLowerCase))
     :headers (get-headers* request)
     :content-type (.. request getBody getContentType getType)
     :content-length (Integer/parseInt (.. request getHeaders (get "Content-Length")))
     :character-encoding (.. request getBody getContentType (getCharset "utf-8"))
     :body (.. request getBody getInputStream)}))

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

(defmulti send!
  "Send data to the client."
  (fn [response data] (class response)))

(defmethod send! DefaultContext
  [^DefaultContext context data]
  (send data (:response context)))

(defmethod send! Response
  [^Response response data]
  (send data response))

(defmulti adapter
  "A polymorphic function for create the
  handler adapter."
  (fn [handler & args] (:type (meta handler)))
  :default :ratpack)

(defmethod adapter :ratpack
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            context-params (ctx/context-params context)
            route-params (ctx/route-params context)
            response (-> (merge context context-params)
                         (assoc :route-params route-params)
                         (handler))]
        (when (satisfies? IHandlerResponse response)
          (handle-response response context))))))

(defmethod adapter :ring
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            request (build-request (:request context))
            response (handler request)]
        (when (satisfies? IHandlerResponse response)
          (handle-response response context))))))
