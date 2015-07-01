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

(ns catacumba.impl.parse
  "Form parsing facilities."
  (:require [catacumba.utils :as utils])
  (:import ratpack.handling.Context
           ratpack.form.Form
           ratpack.http.Request
           ratpack.util.MultiValueMap
           catacumba.impl.context.DefaultContext))

;; Implementation notes:
;; Reflection is used for access to private field of Form instance
;; because ratpack does not allows an other way for iter over
;; all parameters (including files) in an uniform way.

(defn- extract-files
  [^Form form]
  (let [field (.. form getClass (getDeclaredField "files"))]
    (.setAccessible field true)
    (.get field form)))

(defn parse-queryparams
  "Parse query params from request and return a
  maybe multivalue map."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^Request request (.getRequest ctx)
        ^MultiValueMap params (.getQueryParams request)]
    (persistent!
     (reduce (fn [acc key]
               (let [values (.getAll params key)]
                 (reduce #(utils/assoc-conj! %1 key %2) acc values)))
             (transient {})
             (.keySet params)))))

(defn parse-formdata*
  "Parse form encoded or multipart request data and return
  a maybe multivalue map."
  {:no-doc true}
  [^Context ctx]
  (let [^MultiValueMap form (.parse ctx Form)
        ^MultiValueMap files (extract-files form)
        result (transient {})]
    (reduce (fn [acc key]
              (let [values (.getAll files key)]
                (reduce #(utils/assoc-conj! %1 key %2) acc values)))
            result
            (.keySet files))
    (reduce (fn [acc key]
              (let [values (.getAll form key)]
                (reduce #(utils/assoc-conj! %1 key %2) acc values)))
            result
            (.keySet form))
    (persistent! result)))

(defn parse-formdata
  "Parse form encoded or multipart request data and return
  a maybe multivalue map."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)]
    (parse-formdata* ctx)))
