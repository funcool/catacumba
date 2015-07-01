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

(ns catacumba.handlers.cors
  (:require [cuerdas.core :as str]
            [catacumba.impl.context :as ct]
            [catacumba.impl.handlers :as hs])
  (:import ratpack.http.Request
           ratpack.http.Response))

(defn- allow-origin?
  [value {:keys [origin]}]
  (cond
    (nil? value) value
    (= origin "*") origin
    (set? origin) (origin value)
    (= origin value) origin))

(defn- handle-preflight
  [context request headers {:keys [allow-methods allow-headers max-age allow-credentials] :as opts}]
  (let [^Response response (:response context)
        ^String origin (get headers "origin")]
    (when-let [origin (allow-origin? origin opts)]
      (hs/set-headers! response {"Access-Control-Allow-Origin" origin
                                "Access-Control-Allow-Methods" (str/join "," allow-methods)})
      (when allow-credentials
        (hs/set-headers! response {"Access-Control-Allow-Credentials" true}))
      (when max-age
        (hs/set-headers! response {"Access-Control-Max-Age" max-age}))
      (when allow-headers
        (hs/set-headers! response {"Access-Control-Allow-Headers" (str/join "," allow-headers)})))
    (hs/send! context "")))

(defn- handle-response
  [context headers {:keys [allow-headers expose-headers origin] :as opts}]
  (let [^Response response (:response context)
        ^String origin (get headers "origin")]
    (when-let [origin (allow-origin? origin opts)]
      (hs/set-headers! response {"Access-Control-Allow-Origin" origin})
      (when allow-headers
        (hs/set-headers! response {"Access-Control-Allow-Headers" (str/join "," allow-headers)}))
      (when expose-headers
        (hs/set-headers! response {"Access-Control-Expose-Headers" (str/join "," expose-headers)})))
    (ct/delegate context)))

(defn- cors-preflight?
  [^Request request headers]
  (and (.. request getMethod isOptions)
       (contains? headers "origin")
       (contains? headers "access-control-request-method")))

(defn cors
  "A chain handler that handles cors related headers."
  [{:keys [origin] :as opts}]
  (fn [context]
    (let [^Request request (:request context)
          headers (hs/get-headers request)]
      (if (cors-preflight? request headers)
        (handle-preflight context request headers opts)
        (handle-response context headers opts)))))
