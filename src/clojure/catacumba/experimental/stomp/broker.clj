(ns catacumba.experimental.stomp.broker
  "A simple, in memmory pub sub broker implementation."
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async-proto]
            [cuerdas.core :as str]))

(defn- chan?
  "Returns true if `x` is satisfies a Channel protocol."
  [x]
  (satisfies? async-proto/Channel x))

(defn- start-listening
  "Start the listeing loop until subscriber is closed."
  [publication topic listener]
  (let [subscriber (async/chan (async/dropping-buffer 256))]
    (async/sub publication topic subscriber)
    (async/go-loop []
      (when-let [message (async/<! subscriber)]
        (let [r (listener (:payload message))]
          (when (chan? r)
            (async/<! r)))
        (recur)))
    subscriber))

(defn- generate-key
  "Generate a composed key from topic and key."
  [topic key]
  (-> (str (name topic) "-" (name key))
      (str/slugify)
      (keyword)))

(defprotocol IBroker
  (^:private subscribe* [_ topic key listener] "Add subscriptor.")
  (^:private unsubscribe* [_ topic key] "Remove subscription.")
  (^:private send* [_ topic message] "Send a message to the topic."))

(deftype Broker [inputchan publication state]
  IBroker
  (subscribe* [_ topic key listener]
    (let [subscriber (start-listening publication topic listener)
          composedkey (generate-key topic key)]
      (send state assoc composedkey subscriber)))

  (unsubscribe* [_ topic key]
    (let [composedkey (generate-key topic key)
          subscriber (get @state composedkey nil)]
      (when subscriber
        (async/close! subscriber)
        (send state dissoc composedkey))))

  (send* [_ topic message]
    (async/put! inputchan {:topic topic :payload message}))

  java.io.Closeable
  (close [_]
    (reduce (fn [_ [key channel]]
              (async/close! channel)
              (send state dissoc key))
            nil
            @state)))

(defn message-broker
  "The in memory broker engine constructor."
  [& [{:keys [inputbuffer] :or {inputbuffer 256}}]]
  (let [inputchan (async/chan (async/sliding-buffer inputbuffer))
        publication (async/pub inputchan :topic)
        state (agent {})]
    (Broker. inputchan publication state)))

(defn subscribe!
  "Create new subscription."
  [broker topic key listener]
  {:pre [(fn? listener)]}
  (let [topic (keyword topic)
        key (keyword key)]
    (subscribe* broker topic key listener)))

(defn unsubscribe!
  "Unsubscribe the subscriber from a topic."
  [broker topic key]
  (let [topic (keyword topic)
        key (keyword key)]
    (unsubscribe* broker topic key)))

(defn send!
  "Publish message to the topic."
  [broker topic message]
  (let [topic (keyword topic)
        key (keyword key)]
    (send* broker topic message)))
