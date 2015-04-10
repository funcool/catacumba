(ns catacumba.impl.websocket
  "Websocket handler adapter implementation."
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :refer [chan go-loop close! >! <! put!]]
            [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.streams :as streams])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.websocket.WebSocketClose
           ratpack.websocket.WebSocketHandler
           ratpack.websocket.WebSocketMessage
           ratpack.websocket.WebSockets
           ratpack.websocket.WebSocket))

(deftype WebSocketSession [in out ctrl context handler ws]
  java.io.Closeable
  (close [_]
    (close! in)
    (close! out)
    (close! ctrl))

  WebSocketHandler
  (onOpen [this ^WebSocket ws']
    (vreset! ws ws')
    (go-loop []
      (if-let [value (<! out)]
        (do
          (.send ws' (streams/as-byte-buffer value))
          (recur))
        (.close ws')))
    (handler {:in in :out out :ctrl ctrl
              :ws this :context context}))

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
    (->> (WebSocketSession. in out ctrl context handler (volatile! nil))
         (WebSockets/websocket context))))


(defn websocket-adapter
  "Adapt a function based handler into ratpack
  compatible handler instance."
  [handler]
  (reify Handler
    (^void handle [_ ^Context context]
      (websocket context handler))))
