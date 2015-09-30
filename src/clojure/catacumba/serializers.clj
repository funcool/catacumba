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

(ns catacumba.serializers
  "A serializers abstraction layer."
  (:require [cheshire.core :as json]
            [cognitect.transit :as transit]
            [buddy.core.codecs :as codecs])
  (:import java.io.ByteArrayInputStream
           java.io.ByteArrayOutputStream))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- str->bytes
  [data]
  (.getBytes data "UTF-8"))

(defn bytes->str
  [data]
  (String. data "UTF-8"))

(defmulti encode
  "Encode data."
  (fn [data type] type))

(defmulti decode
  "Decode data."
  (fn [data type] type))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod encode :json
  [data _]
  (-> (json/encode data)
      (codecs/str->bytes)))

(defmethod decode :json
  [data _]
  (-> (codecs/bytes->str data)
      (json/decode true)))

(defmethod encode :transit+json
  [data _]
  (with-open [out (ByteArrayOutputStream.)]
    (let [w (transit/writer out :json)]
      (transit/write w data)
      (.toByteArray out))))

(defmethod encode :transit+msgpack
  [data _]
  (with-open [out (ByteArrayOutputStream.)]
    (let [w (transit/writer out :msgpack)]
      (transit/write w data)
      (.toByteArray out))))

(defmethod decode :transit+json
  [data _]
  (with-open [input (ByteArrayInputStream. data)]
    (let [reader (transit/reader input :json)]
      (transit/read reader))))

(defmethod decode :transit+msgpack
  [data _]
  (with-open [input (ByteArrayInputStream. data)]
    (let [reader (transit/reader input :msgpack)]
      (transit/read reader))))
