;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions
;; are met:
;;
;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.
;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
;; IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
;; OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
;; IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
;; INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
;; NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
;; THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.impl.stream.common
  "Defines a common subset of functions and hepers that
  works with all kind of subscription objects."
  (:require [catacumba.impl.atomic :as atomic]
            [promesa.core :as p]
            [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as asyncp])
  (:import clojure.lang.Seqable
           org.reactivestreams.Subscriber
           java.lang.AutoCloseable
           java.util.Set
           java.util.concurrent.CountDownLatch))

(definterface IPullStream
  (pull [] "Pull a value from the stream."))

(defn- publisher->seq
  "Coerce a publisher in a blocking seq."
  [^IPullStream s]
  (lazy-seq
   (let [v @(.pull s)]
     (if v
       (cons v (lazy-seq (publisher->seq s)))
       (.close ^AutoCloseable s)))))

(defn subscribe
  "Create a subscription to the given publisher instance.

  The returned subscription does not consumes the publisher
  data until is requested."
  [p]
  (let [sr (async/chan)
        lc (CountDownLatch. 1)
        ss (atomic/ref nil)
        sb (reify Subscriber
             (onSubscribe [_ s]
               (atomic/set! ss s)
               (.countDown lc))
             (onNext [_ v]
               (async/put! sr v))
             (onError [_ e]
               (async/close! sr))
             (onComplete [_]
               (async/close! sr)))]
    (.subscribe p sb)
    (reify
      AutoCloseable
      (close [_]
        (.await lc)
        (.cancel @ss))

      Seqable
      (seq [this]
        (.await lc)
        (publisher->seq this))

      IPullStream
      (pull [_]
        (.await lc)
        (p/promise (fn [resolve reject]
                     (async/take! sr resolve)
                     (.request @ss 1))))

      asyncp/ReadPort
      (take! [_ handler]
        (asyncp/take! sr handler)))))

(defn proxy-subscriber
  "Create a proxy subscriber.

  The main purpose of this proxy is apply some
  kind of transformations to the proxied publisher
  using transducers."
  [xform subscriber]
  (let [rf (xform (fn
                    ([s] s)
                    ([s v] (.onNext s v))))
        completed (atomic/boolean false)
        subscription (atomic/ref nil)]
    (reify Subscriber
      (onSubscribe [_ s]
        (atomic/set! subscription s)
        (.onSubscribe subscriber s))
      (onNext [_ v]
        (when-not @completed
          (let [res (rf subscriber v)]
            (cond
              (identical? res subscriber)
              (.request @subscription 1)

              (reduced? res)
              (do
                (.cancel @subscription)
                (.onComplete (rf subscriber))
                (atomic/set! completed true))))))
      (onError [_ e]
        (atomic/set! completed true)
        (rf subscriber)
        (.onError subscriber e))
      (onComplete [_]
        (when-not @completed
          (atomic/set! completed true)
          (rf subscriber)
          (.onComplete subscriber))))))
