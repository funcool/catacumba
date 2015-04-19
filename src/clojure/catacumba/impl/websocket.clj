(ns catacumba.impl.websocket
  "Websocket handler adapter implementation."
  (:require [clojure.core.async :refer [chan go-loop close! >! <! put!] :as async]
            [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.streams :as streams])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.func.Action
           ratpack.exec.ExecController
           java.util.concurrent.ExecutorService
           catacumba.websocket.WebSocketClose
           catacumba.websocket.WebSocketHandler
           catacumba.websocket.WebSocketMessage
           catacumba.websocket.WebSockets
           catacumba.websocket.WebSocket))

(defn send!
  [^ExecutorService executor ^WebSocket ws data]
  (let [ch (async/chan)
        callback (fn [_] (async/close! ch))
        task (fn []
               (-> (.send ws (helpers/bytebuffer data))
                   (.then (helpers/action callback))))]
    (.submit executor ^Runnable task)
    ch))

(deftype WebSocketSession [in out ctrl context handler]
  java.io.Closeable
  (close [_]
    (close! in)
    (close! out)
    (close! ctrl))

  WebSocketHandler
  (onOpen [this ^WebSocket ws]
    (let [executor (.. context getController getExecutor)]
      (go-loop []
        (if-let [value (<! out)]
          (do
            (<! (send! executor ws value))
            (recur))
          (.close ws)))
      (handler {:in in :out out :ctrl ctrl
                :ws ws :session this
                :context context})))

  (^void onMessage [_ ^WebSocketMessage msg]
    (put! in (.getText msg)))

  (^void onClose [this ^WebSocketClose event]
    (put! ctrl [:close event])
    (.close this)))

(defn websocket
  [^Context context handler]
  (let [in (chan 256)
        out (chan)
        ctrl (chan)]
    (->> (WebSocketSession. in out ctrl context handler)
         (WebSockets/websocket context))))


(defn websocket-adapter
  "Adapt a function based handler into ratpack
  compatible handler instance."
  [handler]
  (reify Handler
    (^void handle [_ ^Context context]
      (websocket context handler))))
