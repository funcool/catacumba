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
  (:require [clojure.java.io :as io]
            [manifold.stream :as ms]
            [manifold.deferred :as md]
            [promissum.core :as p]
            [catacumba.stream :as stream]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ct]
            [catacumba.impl.helpers :as ch]
            [catacumba.impl.http :as http])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Request
           ratpack.http.Response
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
  (-send [data ctx] "Send data."))

(defprotocol IHandlerResponse
  (-handle-response [_ context] "Handle the ratpack handler response."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol IHandlerResponse
  String
  (-handle-response [data ^DefaultContext context]
    (-send data (:catacumba/context context)))

  clojure.lang.IPersistentMap
  (-handle-response [data ^DefaultContext context]
    (let [{:keys [status headers body]} data]
      (when status (ct/set-status! context status))
      (when headers (ct/set-headers! context headers))
      (-send body (:catacumba/context context))))

  catacumba.impl.http.Response
  (-handle-response [data ^DefaultContext context]
    (ct/set-status! context (:status data))
    (ct/set-headers! context (:headers data))
    (-send (:body data) (:catacumba/context context)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (-handle-response [data ^DefaultContext context]
    (ct/set-status! context 200)
    (-send data (:catacumba/context context)))

  manifold.stream.default.Stream
  (-handle-response [data ^DefaultContext context]
    (ct/set-status! context 200)
    (-send data (:catacumba/context context)))

  Publisher
  (-handle-response [data ^DefaultContext context]
    (ct/set-status! context 200)
    (-send data (:catacumba/context context)))

  CompletableFuture
  (-handle-response [data ^DefaultContext context]
    (ct/set-status! context 200)
    (-send data (:catacumba/context context))))

(extend-protocol ISend
  String
  (-send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (.send response data)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (-send [data ^Context ctx]
    (-> (stream/publisher data)
        (-send ctx)))

  manifold.stream.default.Stream
  (-send [data ^Context ctx]
    (-> (stream/publisher data)
        (-send ctx)))

  manifold.deferred.IDeferred
  (-send [data ^Context ctx]
    (-> (stream/publisher data)
        (-send ctx)))

  CompletableFuture
  (-send [future' ^Context ctx]
    (-> (ch/promise (fn [resolve] (resolve future')))
        (ch/then #(-send % ctx))))

  Publisher
  (-send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (->> (stream/publisher data)
           (stream/transform (map ch/bytebuffer))
           (stream/bind-exec)
           (.sendStream response))))

  ;; TODO: reimplement this as chunked stream instread of
  ;; read all data in memory. The current approach is slightly
  ;; awfull because it reads all inputstream firstly in a memory.
  InputStream
  (-send [data ^Context ctx]
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
    (io/make-reader (:body ctx) opts))
  (make-writer [ctx opts]
    (io/make-writer (:body ctx) opts))
  (make-input-stream [ctx opts]
    (io/make-input-stream (:body ctx) opts))
  (make-output-stream [ctx opts]
    (io/make-output-stream (:body ctx) opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn send!
  [context data]
  (->> (:catacumba/context context)
       (-send data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: implement this in more efficient way
;; The current approach is not bad but I personally
;; prefer find a better one.

(defn hydrate-context
  {:internal true :no-doc true}
  [^Context ctx callback]
  (letfn [(continuation [^Request request ^TypedData body]
            (let [context (ct/context ctx)
                  params (ct/get-context-params context)
                  extra {:body body
                         :path (str "/" (.getPath request))
                         :query (.getQuery request)
                         :method (keyword (.. request getMethod getName toLowerCase))
                         :query-params (ct/get-query-params context)
                         :route-params (ct/get-route-params context)
                         :cookies (ct/get-cookies context)
                         :headers (ct/get-headers context)}]
              (callback (merge context params extra))))]
    (let [^Request request (.getRequest ctx)
          ^Promise promise (.getBody request)]
      (ch/then promise (partial continuation request)))))

(defmethod adapter :catacumba/default
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (hydrate-context ctx (fn [^DefaultContext context]
                             (let [response (handler context)]
                               (when (satisfies? IHandlerResponse response)
                                 (-handle-response response context))))))))

(defmethod adapter :catacumba/blocking
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (hydrate-context ctx (fn [^DefaultContext context]
                             (-> (ch/blocking
                                  (handler context))
                                 (ch/then (fn [response]
                                            (when (satisfies? IHandlerResponse response)
                                              (-handle-response response context))))))))))

(defmethod adapter :catacumba/cps
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (hydrate-context ctx (fn [^DefaultContext context]
                             (-> (ch/promise (fn [resolve] (handler context #(resolve %))))
                                 (ch/then #(-handle-response % context))))))))

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
      :headers (ct/get-headers request)
      :content-type (.. body getContentType getType)
      :content-length (Integer/parseInt (.. request getHeaders (get "Content-Length")))
      :character-encoding (.. body getContentType (getCharset "utf-8"))
      :body (.. body getInputStream)}))

(defmethod adapter :catacumba/ring
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ct/context ctx)
            p (ch/blocking
               (let [request (build-request (:request context))]
                 (handler request)))]
        (ch/then p (fn [response]
                     (when (satisfies? IHandlerResponse response)
                       (-handle-response response context))))))))
