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

(ns catacumba.impl.context
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:require [catacumba.utils :as utils]
            [catacumba.impl.helpers :as helpers])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.handling.RequestOutcome
           ratpack.http.Request
           ratpack.http.Response
           ratpack.server.PublicAddress
           ratpack.registry.Registry))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DefaultContext [^ratpack.http.Request request
                           ^ratpack.http.Response response])

(defrecord ContextData [payload])

(alter-meta! #'->DefaultContext assoc :private true)
(alter-meta! #'map->DefaultContext assoc :private true)
(alter-meta! #'->ContextData assoc :private true)
(alter-meta! #'map->ContextData assoc :private true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn context
  "A catacumba context constructor."
  [^Context context']
  (map->DefaultContext {:catacumba/context context'
                        :request (.getRequest context')
                        :response (.getResponse context')}))


(defn context-params
  "Get the current context params.

  The current params can be passed to the next
  handler using the `delegate` function. Is a simple
  way to communicate the handlers chain."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)]
    (try
      (let [cdata (.get ctx ContextData)]
        (:payload cdata))
      (catch ratpack.registry.NotInRegistryException e
        {}))))

(defn delegate
  "Delegate handling to the next handler in line.

  This function accept an additiona parameter for
  pass context parameters to the next handlers, and
  that can be obtained with `context-params`
  function."
  ([^DefaultContext context]
   (let [^Context ctx (:catacumba/context context)]
     (.next ctx)))
  ([^DefaultContext context data]
   (let [^Context ctx (:catacumba/context context)
         previous (context-params context)
         ^Registry reg (Registry/single (ContextData. (merge previous data)))]
     (.next ctx reg))))

(defn public-address
  "Get the current public address as URI instance.

  The default implementation uses a variety of strategies to
  attempt to provide the desired result most of the time.
  Information used includes:

  - Configured public address URI (optional)
  - X-Forwarded-Host header (if included in request)
  - X-Forwarded-Proto or X-Forwarded-Ssl headers (if included in request)
  - Absolute request URI (if included in request)
  - Host header (if included in request)
  - Service's bind address and scheme (http vs. https)"
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^PublicAddress addr (.get ctx PublicAddress)]
    (.getAddress addr ctx)))

(defn route-params
  "Return a hash-map with parameters extracted from
  routing patterns."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)]
    (into {} utils/keywordice-keys-t (.getPathTokens ctx))))

(defn on-close
  "Register a callback in the context that will be called
  when the connection with the client is closed."
  [^DefaultContext context callback]
  (let [^Context ctx (:catacumba/context context)]
    (.onClose ctx (helpers/action callback))))

(defn before-send
  "Register a callback in the context that will be called
  just before send the response to the client. Is a useful
  hook for set some additional cookies, headers or similar
  response transformations."
  [^DefaultContext context callback]
  (let [^Response response (:response context)]
    (.beforeSend response (helpers/action callback))))
