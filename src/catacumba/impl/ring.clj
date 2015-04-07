(ns catacumba.impl.ring
  (:require [catacumba.utils :as utils]
            [catacumba.impl.ratpack :as ratpack])
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocols definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IRingResponse
  (^:private handle-ring-response [_ context]))

(defprotocol IRingBody
  (^:private handle-ring-body [_ context]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol IRingResponse
  clojure.lang.IPersistentMap
  (handle-ring-response [rsp ^Context context]
  (let [^Response response (ratpack/get-response context)
        {:keys [status headers body]} rsp]
    (when status
      (.status response ^long status))
    (when headers
      (ratpack/set-headers response headers))
    (handle-ring-body body context))))

(extend-protocol IRingBody
  String
  (handle-ring-body [data ^Context context]
    (let [response (ratpack/get-response context)]
      (ratpack/send data response)))

  InputStream
  (handle-ring-body [body ^Context context]
    (let [^Response response (ratpack/get-response context)
          ^bytes buffer (byte-array 1024)
          ^ByteBuf buf (Unpooled/buffer (.available body))]
      (loop [index 0]
        (let [readed (.read body buffer 0 1024)]
          (when-not (= readed -1)
            (.writeBytes buf buffer 0 readed)
            (recur (+ index readed)))))
      (.send response buf))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-request
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
     :headers (ratpack/get-headers request)
     :content-type (.. request getBody getContentType getType)
     :content-length (Integer/parseInt (.. request getHeaders (get "Content-Length")))
     :character-encoding (.. request getBody getContentType (getCharset "utf-8"))
     :body (.. request getBody getInputStream)}))

(defn ring-adapter
  "Adapt the ratpack style handler to ring compatible handler."
  [handler]
  (fn [^Context context]
    (let [request (build-request (.getRequest context))
          response (handler request)]
      (handle-ring-response response context))))
