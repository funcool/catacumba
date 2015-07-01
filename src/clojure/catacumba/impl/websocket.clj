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
           io.netty.buffer.ByteBuf
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
                                  (let [^ByteBuf data (helpers/bytebuffer data)]
                                    (-> (.send ws data)
                                        (.then (helpers/action callback))))))
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
         (WebSockets/websocket ^Context (:catacumba/context context)))))

(defmethod handlers/adapter :catacumba/websocket
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)]
        (websocket context handler)))))
