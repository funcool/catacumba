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

(ns catacumba.handlers.logging
  "Request logging."
  (:require [catacumba.impl.context :as ct]
            [catacumba.core :refer [on-close]])
  (:import ratpack.handling.RequestLogger
           ratpack.handling.RequestOutcome
           ratpack.http.Status))

(defn- status->map [^Status status]
  {:code (.getCode status)
   :message (.getMessage status)})

(defn- outcome->map [^RequestOutcome outcome]
  (let [response (.getResponse outcome)]
    {:headers  (ct/headers->map
                (.. response getHeaders asMultiValueMap)
                true)
     :status   (status->map (.getStatus response))
     :sent-at  (.getSentAt outcome)
     :duration (.getDuration outcome)}))

(defn log
  ([] (RequestLogger/ncsa))
  ([log-fn]
   (fn [context]
     (on-close context #(log-fn context (outcome->map %)))
     (ct/delegate))))
