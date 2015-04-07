(ns catacumba.streams
  (:require [clojure.core.async :refer [put! take! chan <! >! go close! go-loop onto-chan]])
  (:import org.reactivestreams.Publisher
           org.reactivestreams.Subscriber
           org.reactivestreams.Subscription))

(defn- create-subscription
  [ch ^Subscriber subscriber]
  (let [demand (chan 256)]
    (go-loop []
      (when-let [demanded (<! demand)]
        (loop [demandseq (range (- demanded 1))]
          (let [val (<! ch)
                flag (first demandseq)]
            (if (nil? val)
              (do
                (.onComplete subscriber)
                (close! demand))
              (do
                (.onNext subscriber val)
                (when-not (nil? flag)
                  (recur (rest demandseq)))))))
        (recur)))
    (reify Subscription
      (^void request [_ ^long n]
        (put! demand n))
      (^void cancel [_]
        (close! ch)))))

(defn chan->publisher
  "Converts a chan `ch` into the reactive
  streams publisher instance."
  [ch]
  (reify Publisher
    (^void subscribe [_ ^Subscriber subscriber]
      (let [^Subscription subscription (create-subscription ch)]
        (.onSubscribe subscriber subscription)))))
