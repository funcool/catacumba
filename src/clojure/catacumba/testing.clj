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

(ns catacumba.testing
  "Testing facilities for catacuba."
  (:require [catacumba.core :as ct]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]))

(defmacro with-server
  "Evaluate code in context of running catacumba server."
  [{:keys [handler sleep] :or {sleep 50} :as options} & body]
  `(let [server# (ct/run-server ~handler (merge ~options {:debug true}))]
     (try
       ~@body
       (finally
         (.stop server#)
         (Thread/sleep ~sleep)))))

(defn data->transit
  "Simple util to convert clojure data structures into transit"
  ([data]
   (data->transit data :json))
  ([data encoding]
   (with-open [out (ByteArrayOutputStream.)]
     (let [w (transit/writer out encoding)]
       (transit/write w data)
       (.toByteArray out)))))
