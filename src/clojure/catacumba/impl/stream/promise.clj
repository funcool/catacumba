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

(ns catacumba.impl.stream.promise
  (:require [catacumba.impl.atomic :as atomic]
            [catacumba.impl.stream.common :as common]
            [promissum.core :as p])
  (:import org.reactivestreams.Subscriber
           catacumba.impl.stream.common.IPullStream
           java.util.Set))

(declare handle-subscribe)
(declare handle-request)
(declare handle-cancel)

(deftype Subscription [canceled finished publisher subscriber]
  org.reactivestreams.Subscription
  (^void cancel [this]
    (atomic/set! canceled true))

  (^void request [this ^long n]
    (when (and (not @canceled)
               (atomic/compare-and-set! finished false true))
      (handle-request this))))

(deftype Publisher [source]
  clojure.lang.Seqable
  (seq [p]
    (seq (common/subscribe p)))

  org.reactivestreams.Publisher
  (^void subscribe [this ^Subscriber subscriber]
    (let [canceled (atomic/boolean false)
          finished (atomic/boolean false)
          sub (Subscription. canceled finished this subscriber)]
      (try
        (.onSubscribe subscriber ^Subscription sub)
        (catch Throwable t
          (handle-cancel sub)
          (try
            (.onError subscriber (IllegalStateException.
                                  "Violated the Reactive Streams rule 2.13"))
            (catch Throwable t
              (IllegalStateException.
               "Violated the Reactive Streams rule 2.13"))))))))

(defn- handle-cancel
  [^Subscription sub]
  (let [canceled (.-canceled sub)]
    (atomic/compare-and-set! canceled false true)))

(defn- handle-request
  [^Subscription sub]
  (let [^Subscriber subscriber (.-subscriber sub)
        ^Publisher publisher (.-publisher sub)
        canceled (.-canceled sub)
        source (.-source publisher)]
    (p/then source (fn [v]
                     (.onNext subscriber v)
                     (handle-cancel sub)
                     (.onComplete subscriber)))
    (p/catch source (fn [e]
                      (handle-cancel sub)
                      (.onError e)))))

(defn publisher
  "A publisher constructor with promise
  channel as its source. The returned publisher
  instance is of multicast type."
  ([source] (publisher source {}))
  ([source options]
   (Publisher. source)))
