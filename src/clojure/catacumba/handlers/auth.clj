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

(ns catacumba.handlers.auth
  "Authentication and Authorization facilities for catacumba
  using funcool/buddy "
  (:require [catacumba.impl.handlers :as hs]
            [catacumba.impl.routing :as rt]
            [catacumba.impl.context :as ct]
            [catacumba.impl.http]
            [catacumba.impl.helpers :as hp]
            [buddy.sign.jwt :as jwt]
            [promesa.core :as p])
  (:import ratpack.exec.Downstream
           ratpack.exec.Promise
           ratpack.handling.Chain
           ratpack.handling.Handler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; External protocols implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IAuthentication
  "Protocol that defines unfied workflow steps for
  all authentication backends."
  (-parse [_ request]
    "Parse token (from cookie, session or any other
    http header) and return it.")
  (-authenticate [_ context token]
    "Given a request and parsed data (from previous step), tries
    authenticate the current request and return the user entity.
    This function should return a IPromise instance."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- find-header
  "Looks up a header in a headers map case insensitively,
  returning the header map entry, or nil if not present."
  [headers header-name]
  (first (filter #(.equalsIgnoreCase (name header-name) (name (key %))) headers)))

(defn- get-header
  [request header-name]
  (some-> (:headers request) (find-header header-name) val))

(defn- parse-authorization-header
  [request token-name]
  (some->> (get-header request :authorization)
           (re-find (re-pattern (str "^" token-name " (.+)$")))
           (second)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Builtin backends
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn jws-backend
  "The JWS (Json Web Signature) based backend constructor."
  [{:keys [secret options token-name on-error]
    :or {token-name "Token"}}]
  (reify
    IAuthentication
    (-parse [_ context]
      (parse-authorization-header context token-name))
    (-authenticate [_ context token]
      (p/promise (fn [resolve reject]
                   (try
                     (resolve (jwt/unsign token secret options))
                     (catch Exception e
                       (if (fn? on-error)
                         (resolve (on-error context e))
                         (resolve nil)))))))))

(defn jwe-backend
  "The JWE (Json Web Encryption) based backend constructor."
  [{:keys [secret options token-name on-error]
    :or {token-name "Token"}}]
  (reify
    IAuthentication
    (-parse [_ context]
      (parse-authorization-header context token-name))
    (-authenticate [_ context token]
      (p/promise (fn [resolve reject]
                   (try
                     (resolve (jwt/decrypt token secret options))
                     (catch Exception e
                       (if (fn? on-error)
                         (resolve (on-error context e))
                         (resolve nil)))))))))

(defn session-backend
  "Given some options, create a new instance
  of Session backend and return it."
  []
  (reify
    IAuthentication
    (-parse [_ context]
      (:identity @(:session context)))
    (-authenticate [_ context data]
      (p/resolved data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- authenticate
  "Perform an asynchronous recursive loop over all
  provided backends and tries to authenticate with
  all them in order."
  [context [backend & backends]]
  (if backend
    (let [token (-parse backend context)]
      (if (nil? token)
        (authenticate context backends)
        (->> (-authenticate backend context token)
             (p/mapcat (fn [ue]
                         (if (nil? ue)
                           (authenticate context backends)
                           (p/resolved (ct/delegate {:identity ue})))))
             (p/error (fn [e] (ct/delegate {}))))))
    (p/resolved
     (ct/delegate {}))))

(defn auth
  "Authentication chain handler constructor."
  [& backends]
  {:pre [(pos? (count backends))]}
  (fn [context]
    (authenticate context backends)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod rt/attach-route :auth
  [^Chain chain [_ & backends]]
  (let [^Handler handler (-> (apply auth backends)
                             (hs/adapter))]
    (.all chain handler)))
