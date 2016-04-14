;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
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

(ns catacumba.handlers.postal
  "A postal protocol implementation on top of http."
  (:require [clojure.core.async :as a]
            [catacumba.serializers :as sz]
            [catacumba.http :as http]
            [catacumba.impl.helpers :as hp]
            [catacumba.impl.websocket :as implws]
            [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as b64]
            [manifold.deferred :as md]
            [promesa.core :as p])
  (:import ratpack.http.TypedData
           java.util.concurrent.CompletableFuture))

;; --- Data encoding/decoding

(defmulti ^:no-doc -decode
  (fn [data content-type]
    content-type))

(defmulti ^:no-doc -encode
  (fn [data content-type]
    content-type))

(defmethod -decode :application/transit+json
  [data _]
  (sz/decode ^bytes data :transit+json))

(defmethod -encode :application/transit+json
  [data _]
  (sz/encode data :transit+json))

;; --- Implementation

(defn- get-content-type
  [context]
  (let [^TypedData body (:body context)
        ^String content-type (.. body getContentType getType)]
    (if content-type
      (keyword (.toLowerCase content-type))
      :application/transit+json)))

(defn- get-incoming-data
  [context]
  (if (= (:method context) :get)
    (if-let [data (get-in context [:query-params :d] nil)]
      (b64/decode data)
      (byte-array 0))
    (.getBytes ^TypedData (:body context))))

(defn- normalize-frame
  [frame]
  (if (map? frame)
    (let [frame (transient frame)]
      (when-not (:type frame)
        (assoc! frame :type :response))
      (persistent! frame))
    (throw (ex-info "Invalid response format" {}))))

(defn- validate-frame
  [frame]
  (when-not (:type frame)
    (throw (ex-info "Invalid frame format" {})))
  frame)

(defn- frame->http
  [frame content-type]
  (http/ok frame {:headers {:content-type content-type}}))

(defprotocol IExceptionAdapter
  "A protocol that defines the behavior of adapting
  a possible exception that can happens in the body
  of postal handler to a valid postal frame.

  It comes with default implementation for:
  `clojure.lang.ExceptionInfo` and generic
  `java.lang.Throwable`."
  (-adapt-exception [_] "Adapt an exception into proper return message."))

(defprotocol IHandlerResponseMessage
  "A protocol that defines the behavior of how
  the return value of postal frame can be transformed
  into valid frame instance.

  It comes with default implementation for:
  `clojure.lang.PersistentMap`, `java.util.concurrent.CompletableFuture`
  and `manifold.deferred.IDeferred`."
  (-handle-response-message [_ context]))

(extend-protocol IExceptionAdapter
  clojure.lang.ExceptionInfo
  (-adapt-exception [ex]
    (let [data (ex-data ex)]
      {:type :error
       :message (.getMessage ex)
       :data data}))

  java.lang.Throwable
  (-adapt-exception [ex]
    (let [message (str ex)]
      {:type :error
       :message message})))

(extend-protocol IHandlerResponseMessage
  clojure.lang.IPersistentMap
  (-handle-response-message [data content-type]
    (try
      (-> (normalize-frame data)
          (-encode content-type)
          (frame->http content-type))
      (catch Throwable error
        (-> (-adapt-exception error)
            (-encode content-type)
            (frame->http content-type)))))

  CompletableFuture
  (-handle-response-message [data content-type]
    (letfn [(on-success [data]
              (-handle-response-message data content-type))
            (on-fail [error]
              (-> (-adapt-exception error)
                  (-handle-response-message content-type)))]
      (-> data
          (p/then on-success)
          (p/catch on-fail))))

  manifold.deferred.IDeferred
  (-handle-response-message [data content-type]
    (-handle-response-message
     (p/promise #(md/on-realized data %1 %2))
     content-type)))

(defn- dispatch
  [handler context content-type frame]
  (try
    (let [response (->> frame
                        (validate-frame)
                        (handler context))]
      (when response
        (-handle-response-message response content-type)))
    (catch Throwable error
      (-> (-adapt-exception error)
          (-encode content-type)
          (frame->http content-type)))))

(defn- connect-chans
  "Like core.async pipe but reacts on close
  in both sides."
  [from to]
  (a/go-loop []
    (let [v (a/<! from)]
      (if (nil? v)
        (a/close! to)
        (if (a/>! to v)
          (recur)
          (a/close! from)))))
  to)

;; --- Public Api

(defn frame
  "A convenience helper that allow easy create a frame compatible
  hash-map instance.

  ```
  (frame {:num 1})
  ;; => {:type :response :data {:num 1}}

  (frame :error {:num 1})
  ;; => {:type :error :data {:num 1}}
  ```"
  ([data]
   {:type :response :data data})
  ([type data]
   {:type type :data data}))

(defn router
  "Given a postal handler function returns a catacumba compatible
  handler that can be attached to the main catacumba routes:

  ```
  (require '[catacumba.core :as ct])
  (require '[catacumba.handlers.postal :as pc])

  (defmulti -handler
    (comp (juxt :type :dest) second vector))

  (def app (ct/routes [[:post \"api\" (pc/router -handler)]]))
  ```"
  ([handler]
   (router handler {}))
  ([handler options]
   (fn [context]
     (try
       (let [content-type (get-content-type context)
             context (assoc context ::content-type content-type)
             data (get-incoming-data context)
             frame (if (empty? data)
                     {}
                     (-decode data content-type))]
         (dispatch handler context content-type frame))
       (catch Exception e
         (http/unsupported-mediatype (str e)))))))

(defn socket
  "Start a websocket connection from the standard
  postal handler.

  The difference with the default implementation
  this encodes messages using the requested
  contentype (only :application/transit+json at this
  moment) and handles keep-alive messages."
  [context handler]
  (let [content-type (::content-type context)]
    (letfn [(encode-message [msg]
              (-> (normalize-frame msg)
                  (-encode content-type)
                  (codecs/bytes->str)))

            (decode-message [msg]
              (-> (codecs/str->bytes msg)
                  (-decode content-type)))

            (inner-handler [{:keys [in out ctrl] :as context}]
              (let [out-xf (map encode-message)
                    in-xf (comp (map decode-message)
                                (filter #(not= (:type %) :ping)))
                    out' (a/chan 1 out-xf)
                    in' (a/chan 1 in-xf)]

                ;; Connect transformatons
                (connect-chans out' out)
                (connect-chans in in')

                ;; keep-alive loop
                (a/go-loop [n 1]
                  (a/<! (a/timeout 5000))
                  (when (a/>! out' {:type :ping :n n})
                    (recur (inc n))))

                ;; Forward context to the next handler
                (-> context
                    (assoc :out out' :in in')
                    (handler))))]
      (implws/websocket context inner-handler))))
