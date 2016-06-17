;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
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
            [clojure.core.async :as a]
            [manifold.stream :as ms]
            [manifold.deferred :as md]
            [promesa.core :as p]
            [catacumba.stream :as stream]
            [catacumba.impl.helpers :as hp]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.http :as http])
  (:import catacumba.impl.DelegatedContext
           catacumba.impl.ContextHolder
           java.io.InputStream
           java.io.BufferedReader
           java.io.InputStreamReader
           java.io.BufferedInputStream
           java.util.Map
           java.util.Optional
           java.util.concurrent.CompletableFuture
           com.google.common.net.HostAndPort
           ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.render.Renderable
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.util.MultiValueMap
           ratpack.exec.Downstream
           ratpack.exec.Promise
           ratpack.exec.Blocking
           ratpack.registry.Registry
           org.apache.commons.io.IOUtils
           org.reactivestreams.Publisher
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISend
  (-send [data ctx] "Send data."))

(defprotocol IHandlerResponse
  (-handle-response [_ context] "Handle the ratpack handler response."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol IHandlerResponse
  DelegatedContext
  (-handle-response [this context]
    (let [^Context ctx (:catacumba/context context)]
      (if (.isEmpty this)
        (.next ctx)
        (if-let [^DelegatedContext dc (hp/maybe-get ctx DelegatedContext)]
          (.next ctx (Registry/single
                      (DelegatedContext. (merge (.-data dc)
                                                (.-data this)))))
          (.next ctx (Registry/single this))))))

  java.nio.file.Path
  (-handle-response [path context]
    (-send path (:catacumba/context context)))

  String
  (-handle-response [data context]
    (-send data (:catacumba/context context)))

  clojure.lang.IPersistentMap
  (-handle-response [data context]
    (let [{:keys [status headers body]} data]
      (when status (ctx/set-status! context status))
      (when headers (ctx/set-headers! context headers))
      (-send body (:catacumba/context context))))

  catacumba.impl.http.Response
  (-handle-response [data context]
    (ctx/set-status! context (:status data))
    (ctx/set-headers! context (:headers data))
    (-send (:body data) (:catacumba/context context)))

  clojure.core.async.impl.channels.ManyToManyChannel
  (-handle-response [data context]
    (-> (hp/promise #(a/take! data %))
        (hp/then #(-handle-response % context))))

  manifold.deferred.IDeferred
  (-handle-response [data context]
    (-> (hp/promise #(md/on-realized data %1 %1))
        (hp/then #(-handle-response % context))))

  CompletableFuture
  (-handle-response [data context]
    (let [promise (hp/completable-future->promise data)]
      (-handle-response promise context)))

  ratpack.exec.Promise
  (-handle-response [data context]
    (hp/then data (fn [response]
                    (when (satisfies? IHandlerResponse data)
                      (-handle-response response context))))))

(extend-protocol ISend
  (Class/forName "[B")
  (-send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (.send response ^bytes data)))

  java.nio.file.Path
  (-send [path ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (.sendFile response path)))

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
    (-> (hp/promise #(md/on-realized data %1 %1))
        (hp/then #(-send % ctx))))

  CompletableFuture
  (-send [future' ^Context ctx]
    (-> (hp/promise (fn [resolve] (resolve future')))
        (hp/then #(-send % ctx))))

  Publisher
  (-send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (->> (stream/publisher data)
           (stream/transform (map hp/bytebuffer))
           (stream/bind-exec)
           (.sendStream response))))

  InputStream
  (-send [data ^Context ctx]
    (let [^Response response (.getResponse ctx)]
      (-> (hp/blocking (IOUtils/toByteArray data))
          (hp/then (fn [^bytes buff]
                     (.send response buff)))))))

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
    (throw (UnsupportedOperationException. "Cannot open as Reader."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti adapter
  "A polymorphic function for adapt catacumba
  handlers to ratpack compatible handlers.

  The dispatch is done by `:handler-type` key in the
  metadata found on the given var or anonymous
  handler."
  (fn [handler & args]
    (cond
      (instance? Handler handler)
      :catacumba/native

      (symbol? handler)
      ::symbol

      :else
      (let [metadata (meta handler)]
        (:handler-type metadata))))

  :default :catacumba/default)

(defn send!
  [context data]
  (->> (:catacumba/context context)
       (-send data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod adapter :catacumba/default
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
     (let [context (ctx/create-context ctx)
           response (handler context)]
       (when (satisfies? IHandlerResponse response)
         (-handle-response response context))))))

(defmethod adapter :catacumba/native
  [handler]
  handler)

(defmethod adapter ::symbol
  [handler]
  (adapter (hp/resolve-fn handler)))

(defmethod adapter :catacumba/blocking
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
     (let [context (ctx/create-context ctx)]
       (-> (hp/blocking (handler context))
           (hp/then (fn [response]
                      (when (satisfies? IHandlerResponse response)
                        (-handle-response response context)))))))))

(defmethod adapter :catacumba/cps
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
     (let [context (ctx/create-context ctx)]
       (-> (hp/promise (fn [resolve] (handler context #(resolve %))))
           (hp/then #(-handle-response % context)))))))
