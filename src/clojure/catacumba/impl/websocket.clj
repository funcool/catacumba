(ns catacumba.impl.websocket
  "Websocket handler adapter implementation."
  (:require [clojure.core.async :refer [chan go-loop close! >! <! put!] :as async]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.handlers :as handlers])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.func.Action
           ratpack.exec.ExecController
           java.util.concurrent.ExecutorService
           catacumba.impl.context.DefaultContext
           catacumba.websocket.WebSocketClose
           catacumba.websocket.WebSocketHandler
           catacumba.websocket.WebSocketMessage
           catacumba.websocket.WebSockets
           catacumba.websocket.WebSocket))

(defn- send!
  "Helper function for send data from the netty/ratpack
  eventloop threadpool instead from the core.async."
  [^ExecutorService executor ^WebSocket ws data]
  (let [ch (async/chan)
        callback (fn [_] (async/close! ch))]
    (.submit executor ^Runnable (fn []
                                  (-> (.send ws (helpers/bytebuffer data))
                                      (.then (helpers/action callback)))))
    ch))

(deftype WebSocketSession [in out ctrl context handler]
  java.io.Closeable
  (close [_]
    (close! in)
    (close! out)
    (close! ctrl))

  WebSocketHandler
  (onOpen [this ^WebSocket ws]
    (let [^Context ctx (:catacumba/context context)
          executor (.. ctx getController getExecutor)]
      (go-loop []
        (if-let [value (<! out)]
          (do
            (<! (send! executor ws value))
            (recur))
          (.close ws)))
      (handler {:in in :out out :ctrl ctrl
                :ws ws :session this
                :context context})))

  (^void onMessage [_ ^WebSocketMessage msg ^Action callback]
    (put! in (.getText msg) (fn [_]
                              (.execute callback nil))))

  (^void onClose [this ^WebSocketClose event]
    (put! ctrl [:close event])
    (.close this)))

(defn websocket
  [^DefaultContext context handler]
  (let [in (chan 256)
        out (chan)
        ctrl (chan)]
    (->> (WebSocketSession. in out ctrl context handler)
         (WebSockets/websocket (:catacumba/context context)))))

(defmethod handlers/adapter :websocket
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)]
        (websocket context handler)))))
