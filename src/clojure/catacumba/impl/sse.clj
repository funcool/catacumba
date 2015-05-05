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

(defn- channel->publisher
  "Create a publisher with core.async channel as source."
  [source]
  (reify Publisher
    (^void subscribe [_ ^Subscriber subscriber]
      (let [subscription (stream/chan->subscription subscriber source true)]
        (.onSubscribe subscriber subscription)))))

(defn sse
  "Start the sse connection with the client
  and dispatch it in a special hanlder."
  [^DefaultContext context handler]
  (let [out (async/chan 1 (map event))
        pub (channel->publisher out)
        tfm (helpers/action transform-event)
        sse' (ServerSentEvents/serverSentEvents pub tfm)
        ctx (:catacumba/context context)]
    (async/thread
      (handler context out))
    (.render ctx sse')))

(defmethod handlers/adapter :catacumba/sse
  [handler]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (let [context (ctx/context ctx)
            context-params (ctx/context-params context)
            route-params (ctx/route-params context)
            context (-> (merge context context-params)
                        (assoc :route-params route-params))]
        (sse context handler)))))


