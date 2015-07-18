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

(ns catacumba.stream
  (:require [catacumba.impl.stream :as stream]
            [catacumba.impl.stream.common :as stream.common])
  (:import org.reactivestreams.Publisher
           org.reactivestreams.Subscriber
           catacumba.impl.stream.pushstream.IPushStream
           catacumba.impl.stream.common.IPullStream
           clojure.lang.Seqable))

(defn publisher
  "A polymorphic publisher constructor."
  [source]
  (stream/publisher source))

(defn transform
  "A polymorphic publisher transformer."
  [xform ^Publisher publisher]
  (reify
    Seqable
    (seq [p]
      (seq (stream.common/subscribe p)))

    Publisher
    (^void subscribe [_ ^Subscriber subscriber]
      (let [subscriber (stream.common/proxy-subscriber xform subscriber)]
        (.subscribe publisher subscriber)))))

(defn put!
  "Puts a value into a stream, returning a promise that yields true
  if it succeeds, and false if it fails."
  [^IPushStream p v]
  (.push p v))

(defn take!
  "Takes a value from a stream, returning a deferred that yields the value
  when it is available or nil if the take fails."
  [^IPullStream p]
  (.pull p))

(defn subscribe
  "Create a subscription to the given publisher instance.

  The returned subscription does not consumes the publisher
  data until is requested."
  [p]
  (stream.common/subscribe p))
