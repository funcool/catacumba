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
            [promissum.core :as p]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.handlers :as hs])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.func.Action
           ratpack.exec.ExecController
           java.util.concurrent.ExecutorService
           io.netty.buffer.ByteBuf
           catacumba.impl.context.DefaultContext
           catacumba.websocket.WebSocketHandler
           catacumba.websocket.WebSocketMessage
           catacumba.websocket.WebSockets
           catacumba.websocket.WebSocket))

;; TODO: make it polymorphic and extensible

(defn- send!
  [ws data]
  (let [c (async/chan)]
    (p/then (.send ws data)
            (fn [_]
              (async/close! c)))
    c))

(deftype WebSocketSession [in out ctrl context handler]
  java.io.Closeable
  (close [_]
    (async/close! in)
    (async/close! out)
    (async/close! ctrl))

  WebSocketHandler
  (^void onOpen [this ^WebSocket ws]
    (let [^Context ctx (:catacumba/context context)]
      (async/go-loop []
        (if-let [value (async/<! out)]
          (do
            (async/<! (send! ws (str value)))
            (recur))
          (.close ws)))
      (handler {:in in :out out :ctrl ctrl
                :ws ws :session this
                :context context})))

  (^void onMessage [_ ^WebSocketMessage msg ^Action callback]
    (async/put! in (.getData msg) (fn [_] (.execute callback nil))))

  (^void onClose [this]
    (async/put! ctrl [:close nil])
    (.close this)))

(defn websocket
  [^DefaultContext context handler]
  (let [in (chan 256)
        out (chan)
        ctrl (chan)]
    (->> (WebSocketSession. in out ctrl context handler)
         (WebSockets/websocket ^Context (:catacumba/context context)))))

(defmethod hs/adapter :catacumba/websocket
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (hs/hydrate-context ctx (fn [^DefaultContext context]
                             (websocket context handler))))))
