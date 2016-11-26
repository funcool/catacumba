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

(ns catacumba.impl.sse
  "Server-Sent Events handler adapter implementation."
  ;; TODO: replace direct var usage to fully namespaced calls of core.async
  (:require [clojure.core.async :as a]
            [catacumba.stream :as stream]
            [catacumba.impl.helpers :as hp]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.executor :as exec]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.stream.channel :as schannel])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.http.Response
           ratpack.func.Action
           io.netty.buffer.ByteBufAllocator
           org.reactivestreams.Publisher
           org.reactivestreams.Subscriber
           org.reactivestreams.Subscription))

(deftype Event [id event data]
  Object
  (toString [_]
    (with-out-str
      (when id (println "id:" id))
      (when event (println "event:" event))
      (when data (println "data:" data))
      (println))))

(defprotocol IEventFactory
  (-make-event [_] "coerce to new event instance"))

(extend-protocol IEventFactory
  String
  (-make-event [data]
    (Event. nil nil data))

  Long
  (-make-event [d]
    (-make-event (str d)))

  Event
  (-make-event [event]
    event)

  clojure.lang.IPersistentMap
  (-make-event [data]
    (let [{:keys [id event data]} data]
      (when (and (not id) (not event) (not data))
        (throw (ex-info "You must supply at least one of data, event, id" {})))
      (Event. id event data))))


(defn sse
  "Start the sse connection with the client
  and dispatch it in a special hanlder."
  [context handler]
  (let [^Context ctx (:catacumba/context context)
        ^Response response (:catacumba/response context)
        xform (comp (map -make-event)
                    (map str)
                    (map hp/bytebuffer))
        out (a/chan 1 xform)
        ctrl (a/chan (a/sliding-buffer 1))
        on-cancel (fn []
                    (a/close! out)
                    (a/put! ctrl [:close nil])
                    (a/close! ctrl))
        stream (schannel/publisher out {:on-cancel on-cancel})
        headers {:content-type "text/event-stream;charset=UTF-8"
                 :transfer-encoding "chunked"
                 :cache-control "no-cache, no-store, max-age=0, must-revalidate"
                 :pragma "no-cache"}]
    (handler (assoc context :out out :ctrl ctrl))
    (ctx/set-headers! response headers)
    (.sendStream response (stream/bind-exec stream))))

(defmethod handlers/adapter :catacumba/sse
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
     (let [context (ctx/create-context ctx)]
       (sse context handler)))))


