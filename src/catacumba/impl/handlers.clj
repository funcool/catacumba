(ns catacumba.impl.handlers
  (:refer-clojure :exclude [send])
  (:require [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.http :as http]
            [catacumba.impl.streams :as streams])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.MutableHeaders
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           java.io.InputStream
           java.util.Map))

(declare get-response)
(declare set-response-headers!)
(declare get-request-headers)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISend
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
      (send data response)))

  clojure.lang.IPersistentMap
  (handle-response [data ^Context context]
    (let [^Response response (get-response context)
          {:keys [status headers body]} data]
      (when status
        (.status response ^long status))
      (when headers
        (set-response-headers! response headers))
      (send body response)))

  catacumba.impl.http.Response
  (handle-response [data ^Context context]
    (let [^Response response (get-response context)]
      (.status response ^long (:status data))
      (set-response-headers! response (:headers data))
      (send (:body data) response))))

(extend-protocol ISend
  String
  (send [data ^Response response]
    (.send response data))

  clojure.core.async.impl.channels.ManyToManyChannel
  (send [data ^Response response]
    (let [publisher (streams/chan->publisher data)]
      (.sendStream response publisher)))

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
     :headers (get-request-headers request)
     :content-type (.. request getBody getContentType getType)
     :content-length (Integer/parseInt (.. request getHeaders (get "Content-Length")))
     :character-encoding (.. request getBody getContentType (getCharset "utf-8"))
     :body (.. request getBody getInputStream)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-response
  "Get a response object from Context instance."
  [^Context ctx]
  (.getResponse ctx))

(defn get-request
  "Get a request object from Context instance."
  [^Context ctx]
  (.getRequest ctx))

(defn get-request-headers
  "Get normalized request headers from Context instance."
  [request]
  (let [^Request request (if (instance? Context request)
                           (get-request request)
                           request)
        ^Headers headers (.getHeaders request)
        ^Map headers (.asMultiValueMap headers)]
    (into {} utils/lowercase-keys-t headers)))

(defn set-response-headers!
  "Given a context instance set response headers."
  [response headers]
  (let [^Response response (if (instance? Context response)
                             (get-response response)
                             response)
        ^MutableHeaders headersmap (.getHeaders response)]
    (loop [headers headers]
      (when-let [[key vals] (first headers)]
        (.set headersmap (name key) vals)
        (recur (rest headers))))))

(defn send!
  "Send data to the client."
  [^Context context data]
  (let [response (get-response context)]
    (send data response)))

(defn ratpack-adapter
  "Adapt a function based handler into ratpack
  compatible handler instance."
  [handler]
  (reify Handler
    (^void handle [_ ^Context context]
      (let [response (handler context)]
        (when (satisfies? IHandlerResponse response)
          (handle-response response context))))))

(defn ring-adapter
  "Adapt the ratpack style handler to ring compatible handler."
  [handler]
  (reify Handler
    (^void handle [_ ^Context context]
      (let [request (build-request (get-request context))
            response (handler request)]
        (when (satisfies? IHandlerResponse response)
          (handle-response response context))))))
