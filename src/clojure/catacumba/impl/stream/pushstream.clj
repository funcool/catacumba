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

(ns catacumba.impl.stream.pushstream
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as asyncp]
            [manifold.stream :as ms]
            [manifold.deferred :as md]
            [catacumba.impl.atomic :as atomic]
            [catacumba.impl.stream.common :as common]
            [catacumba.impl.stream.channel :as channel]
            [promissum.core :as p])
  (:import clojure.lang.Seqable
           org.reactivestreams.Publisher
           org.reactivestreams.Subscriber))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default Abstractions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(definterface IPushStream
  (push [v] "Push a value into stream.")
  (complete [] "Mark publisher as complete."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publisher
  "Creates an empty publisher that implements the
  push stream protocol."
  ([bufflen] (publisher bufflen {}))
  ([bufflen _]
   (let [source (async/chan bufflen)
         publsh (channel/publisher source)]
     (reify
       IPushStream
       (push [_ v]
         (let [p (p/promise)]
           (async/put! source v (fn [res]
                                  (if res
                                    (p/deliver p true)
                                    (p/deliver p false))))
           p))

       (complete [_]
         (async/close! source))

       Seqable
       (seq [p]
         (seq (common/subscribe p)))

       Publisher
       (^void subscribe [_ ^Subscriber subscriber]
         (.subscribe publsh subscriber))))))

