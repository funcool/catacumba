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

(ns catacumba.handlers.parsing
  (:require [catacumba.impl.context :as ct]
            [cheshire.core :as json])
  (:import ratpack.http.Request
           ratpack.http.TypedData
           ratpack.handling.Context))

(defmulti parse
  "A polymorophic method for parse body into clojure
  friendly data structure."
  (fn [^Context ctx ^TypedData body]
    (let [^Request request (.getRequest ctx)
          ^String contenttype (.. request getBody getContentType getType)]
      (if contenttype
        (keyword (.toLowerCase contenttype))
        :application/octet-stream)))
  :default :application/octet-stream)

(defmethod parse :multipart/form-data
  [^Context ctx ^TypedData body]
  (ct/get-formdata ctx))

(defmethod parse :application/x-www-form-urlencoded
  [^Context ctx ^TypedData body]
  (ct/get-formdata ctx))

(defmethod parse :application/json
  [^Context ctx ^TypedData body]
  (let [^String data (slurp body)]
    (json/parse-string data true)))

(defmethod parse :application/octet-stream
  [^Context ctx ^TypedData body]
  nil)

(defn body-params
  "A route chain that parses the body into
  a clojure friendly data structure.

  This function optionally accept a used defined method
  or multimethod where to delegate the body parsing."
  ([] (body-params parse))
  ([parsefn]
   (fn [context]
     (let [^Context ctx (:catacumba/context context)
           ^TypedData body (.. ctx getRequest getBody)]
       (ct/delegate context {:body (parsefn ctx body)})))))
