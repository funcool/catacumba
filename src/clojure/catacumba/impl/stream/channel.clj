;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
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

(ns catacumba.impl.stream.channel
  (:require [catacumba.impl.atomic :as atomic]
            [catacumba.impl.stream.common :as common]
            [catacumba.impl.executor :as executor]
            [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as asyncp])
  (:import clojure.lang.Seqable
           org.reactivestreams.Subscriber
           catacumba.impl.stream.common.IPullStream
           java.lang.AutoCloseable
           java.util.Set
           java.util.HashSet
           java.util.Queue
           java.util.Collections
           java.util.concurrent.Executor
           java.util.concurrent.ConcurrentLinkedQueue))

(declare signal-cancel)
(declare signal-request)
(declare signal-subscribe)
(declare subscribe)
(declare schedule)
(declare handle-subscribe)
(declare handle-request)
(declare handle-send)
(declare handle-cancel)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Subscription [canceled active demand queue publisher subscriber]
  org.reactivestreams.Subscription
  (^void cancel [this]
    (signal-cancel this))

  (^void request [this ^long n]
    (signal-request this n))

  Runnable
  (^void run [this]
    (try
      (let [signal (.poll ^Queue queue)]
        (when (not @canceled)
          (case (:type signal)
            ::request (handle-request this (:number signal))
            ::send (handle-send this)
            ::cancel (handle-cancel this)
            ::subscribe (handle-subscribe this))))
      (finally
        (atomic/set! active false)
        (when-not (.isEmpty ^Queue queue)
          (schedule this))))))

(declare terminate)

(deftype Publisher [source subscriptions options]
  clojure.lang.Seqable
  (seq [p]
    (seq (common/subscribe p)))

  org.reactivestreams.Publisher
  (^void subscribe [this ^Subscriber subscriber]
    (let [sub (Subscription.
               (atomic/boolean false)
               (atomic/boolean false)
               (atomic/long 0)
               (ConcurrentLinkedQueue.)
               this
               subscriber)]
      (.add ^Set subscriptions sub)
      ;; (try
      ;;   (.onSubscribe subscriber sub)
      ;;   (catch Throwable t
      ;;     (terminate sub (IllegalStateException. "Violated the Reactive Streams rule 2.13"))))
      (signal-subscribe sub)
      sub)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- terminate
  "Mark a subscrition as terminated
  with provided exception."
  [^Subscription sub e]
  (let [^Subscriber subscriber (.-subscriber sub)]
    (handle-cancel sub)
    (try
      (.onError subscriber e)
      (catch Throwable t
        (IllegalStateException. "Violated the Reactive Streams rule 2.13")))))

(defn- schedule
  "Schedule the subscrption to be executed
  in builtin scheduler executor."
  [^Subscription sub]
  (let [active (.-active sub)
        canceled (.-canceled sub)
        queue (.-queue sub)]
    (when (atomic/compare-and-set! active false true)
      (try
        (.execute ^Executor executor/*default* ^Runnable sub)
        (catch Throwable t
          (when (not @canceled)
            (atomic/set! canceled true)
            (try
              (terminate sub (IllegalStateException. "Unavailable executor."))
              (finally
                (.clear ^Queue queue)
                (atomic/set! active false)))))))))

(defn- signal
  "Notify the subscription about specific event."
  [sub m]
  (let [^Queue queue (.-queue sub)]
    (when (.offer queue m)
      (schedule sub))))

(defn- signal-request
  "Signal the request event."
  [sub n]
  (signal sub {:type ::request :number n}))

(defn- signal-send
  "Signal the send event."
  [sub]
  (signal sub {:type ::send}))

(defn- signal-subscribe
  "Signal the subscribe event."
  [sub]
  (signal sub {:type ::subscribe}))

(defn- signal-cancel
  "Signal the cancel event."
  [sub]
  (signal sub {:type ::cancel}))

(defn- handle-request
  "A generic implementation for request events
  handling for any type of subscriptions."
  [^Subscription sub n]
  (let [demand (.-demand sub)]
    (cond
      (< n 1)
      (terminate sub (IllegalStateException. "violated the Reactive Streams rule 3.9"))

      (< (+ @demand n) 1)
      (do
        (atomic/set! demand Long/MAX_VALUE)
        (handle-send sub))

      :else
      (do
        (atomic/get-and-add! demand n)
        (handle-send sub)))))

(defn- handle-cancel
  [^Subscription sub]
  (let [^Publisher publisher (.-publisher sub)
        ^Set subscriptions (.-subscriptions publisher)
        canceled (.-canceled sub)
        options (.-options publisher)
        source (.-source publisher)]
    (when (not @canceled)
      (atomic/set! canceled true)
      (.remove subscriptions sub)
      (when (:close options)
        (async/close! source)))))

(defn- handle-subscribe
  [^Subscription sub]
  (let [^Publisher publisher (.-publisher sub)
        ^Subscriber subscriber (.-subscriber sub)
        source (.-source publisher)
        canceled (.-canceled sub)]
    (try
      (.onSubscribe subscriber sub)
      (catch Throwable t
        (terminate sub (IllegalStateException. "Violated the Reactive Streams rule 2.13"))))
    (when (asyncp/closed? source)
      (try
        (handle-cancel sub)
        (.onComplete subscriber)
        (catch Throwable t
          ;; (IllegalStateException. "Violated the Reactive Streams rule 2.13")
          )))))

(defn- handle-send
  [sub]
  (let [^Publisher publisher (.-publisher sub)
        ^Subscriber subscriber (.-subscriber sub)
        source (.-source publisher)
        canceled (.-canceled sub)]
    (async/take! source (fn [value]
                          (try
                            (if (nil? value)
                              (do
                                (handle-cancel sub)
                                (.onComplete subscriber))
                              (let [demand (atomic/dec-and-get! (.-demand sub))]
                                (.onNext subscriber value)
                                (when (and (not @canceled) (pos? demand))
                                  (signal-send sub))))
                            (catch Throwable t
                              (handle-cancel sub)))))))

(defn publisher
  "A publisher constructor with core.async
  channel as its source. The returned publisher
  instance is of unicast type."
  ([source] (publisher source {}))
  ([source options]
  (let [subscriptions (Collections/synchronizedSet (HashSet.))]
    (Publisher. source subscriptions options))))
