(ns catacumba.impl.sse
  "Server-Sent Events handler adapter implementation."
  (:require [clojure.core.async :refer [chan go-loop close! >! <! put!] :as async]
            [futura.stream :as stream]
            [catacumba.utils :as utils]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.handlers :as handlers])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.sse.Event
           ratpack.sse.internal.DefaultEvent
           ratpack.sse.ServerSentEvents
           ratpack.func.Action
           catacumba.impl.context.DefaultContext))


(defprotocol IEvent
  (event [_] "Create a event"))

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
  [^DefaultContext context handler]
  (let [out (async/chan 1 (map event))
        pub (stream/publisher out)
        ^ServerSentEvents sse' (ServerSentEvents/serverSentEvents pub (helpers/action transform-event))
        ^Context ctx (:catacumba/context context)]
    (async/thread
      (handler context out))
    (.render ctx sse')))

(defmethod handlers/adapter :sse
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)]
        (sse context handler)))))


