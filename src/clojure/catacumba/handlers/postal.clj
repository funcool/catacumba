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

(ns catacumba.handlers.postal
  "A postal protocol implementation on top of http."
  (:require [catacumba.serializers :as sz]
            [catacumba.http :as http]
            [manifold.deferred :as md]
            [promissum.core :as p]
            [cats.core :as m]
            [cats.monad.exception :as exc])
  (:import ratpack.http.TypedData
           java.util.concurrent.CompletableFuture))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data encoding/decoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti decode
  (fn [data content-type]
    content-type))

(defmulti encode
  (fn [data content-type]
    content-type))

(defmethod decode :application/transit+json
  [data _]
  (sz/decode ^bytes data :transit+json))

(defmethod encode :application/transit+json
  [data _]
  (sz/encode data :transit+json))

(defmethod decode :default
  [_ _]
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-content-type
  [^TypedData body]
  (let [^String content-type (.. body getContentType getType)]
    (when content-type
      (keyword (.toLowerCase content-type)))))

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
  (-adapt-exception [_] "Adapt an exception into proper return message."))

(defprotocol IHandlerResponseMessage
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
          (encode content-type)
          (frame->http content-type))
      (catch Throwable error
        (-> (-adapt-exception error)
            (encode content-type)
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
     (p/promise #(md/on-realized data % %))
     content-type)))

(defn- dispatch
  [handler context content-type frame]
  (try
    (as-> frame frame
      (validate-frame frame)
      (handler context frame)
      (-handle-response-message frame content-type))
    (catch Throwable error
      (-> (-adapt-exception error)
          (encode content-type)
          (frame->http content-type)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn response
  "A convenience helper for easy
  create postal response messages."
  ([data]
   {:type :response :data data})
  ([type data]
   {:type type :data data}))

(defn router
  "A postal message router chain handler."
  ([handler]
   (router handler {}))
  ([handler options]
   (fn [context]
     (let [body (:body context)
           data (.getBytes ^TypedData (:body context))
           content-type (get-content-type body)
           frame (exc/try-on (decode data content-type))]
       (if (and (exc/success? frame) (not (nil? @frame)))
         (dispatch handler context content-type @frame)
         (http/unsupported-mediatype (str (m/extract frame))))))))
