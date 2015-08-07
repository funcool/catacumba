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

(ns catacumba.impl.sse
  "Server-Sent Events handler adapter implementation."
  (:require [clojure.core.async :refer [chan go-loop close! >! <! put!] :as async]
            [catacumba.utils :as utils]
            [catacumba.stream :as stream]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as ch]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.stream.channel :as schannel])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.sse.Event
           ratpack.sse.internal.DefaultEvent
           ratpack.sse.ServerSentEvents
           ratpack.func.Action
           org.reactivestreams.Publisher
           org.reactivestreams.Subscriber
           org.reactivestreams.Subscription
           catacumba.impl.context.DefaultContext))

(defprotocol IEvent
  (^:private event [_] "Create a event"))

(extend-protocol IEvent
  String
  (event [d]
    {:data d})

  Long
  (event [d]
    {:data (str d)})

  clojure.lang.IPersistentMap
  (event [d] d))

(defn- transform-event
  [^Event event']
  (let [{:keys [data event id]} (.getItem event')]
    (when data (.data event' data))
    (when event (.event event' event))
    (when id (.id event' id))
    event'))

(defn sse
  "Start the sse connection with the client
  and dispatch it in a special hanlder."
  [^DefaultContext context handler]
  (let [^Context ctx (:catacumba/context context)
        out (async/chan 1 (map event))
        pub (->> (schannel/publisher out {:close true})
                 (stream/bind-exec))
        tfm (ch/fn->action transform-event)]

    ;; TODO: use own executors instead of core.async thread
    (async/thread
      (handler context out))
    (->> (ServerSentEvents/serverSentEvents pub tfm)
         (.render ctx))))

(defmethod handlers/adapter :catacumba/sse
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (handlers/hydrate-context ctx (fn [context]
                                      (sse context handler))))))


