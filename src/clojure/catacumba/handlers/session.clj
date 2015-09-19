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

(ns catacumba.handlers.session
  "Http Sessions support for Catacumba."
  (:refer-clojure :exclude [empty?])
  (:require [promissum.core :as p]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codecs]
            [promissum.core :as p]
            [catacumba.impl.atomic :as atomic]
            [catacumba.impl.handlers :as hs]
            [catacumba.impl.context :as ct]
            [catacumba.helpers :as hp])
  (:import clojure.lang.IAtom
           clojure.lang.IDeref
           clojure.lang.Counted
           clojure.lang.IFn
           clojure.lang.ISeq
           ratpack.exec.Downstream
           ratpack.exec.Promise
           ratpack.handling.Context
           ratpack.http.Response))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const default-cookie-name :sessionid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Abstractions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISession
  (-get-id [_] "Get the session id.")
  (-empty? [_] "Check if the session is empty.")
  (-accessed? [_] "Check if session is accessed")
  (-modified? [_] "Check if session is modified"))

(defprotocol ISessionStorage
  (-read [_ key] "")
  (-write [_ key data] "")
  (-delete [_ key] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Session [^IAtom data sessionid accessed modified]
  IAtom
  (swap [_ ^IFn f]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.swap data f))
  (swap [_ ^IFn f, ^Object arg]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.swap data f arg))
  (swap [_ ^IFn f, ^Object arg1, ^Object arg2]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.swap data f arg1 arg2))
  (swap[_ ^IFn f, ^Object arg1, ^Object arg2, ^ISeq args]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.swap data f arg1 arg2 args))
  (^boolean compareAndSet [_ ^Object oldv ^Object newv]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.compareAndSet data oldv newv))
  (reset [_ ^Object newv]
    (atomic/compare-and-set! modified false true)
    (atomic/compare-and-set! accessed false true)
    (.reset data newv))

  Counted
  (count [_]
    (.count data))

  IDeref
  (deref [_]
    (atomic/compare-and-set! accessed false true)
    (.deref data))

  ISession
  (-get-id [_] sessionid)
  (-empty? [_] (= (count @data) 0))
  (-accessed? [_] @accessed)
  (-modified? [_] @modified))

(alter-meta! #'->Session assoc :private true)

(defn- ->session
  "A session object constructor."
  ([sessionid] (->session sessionid {}))
  ([sessionid data]
   (Session. (atom data)
             sessionid
             (atomic/boolean false)
             (atomic/boolean false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Storages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn memory-storage
  "In memmory session storage constructor."
  []
  (let [internalstore (atom {})]
    (reify ISessionStorage
      clojure.lang.IDeref
      (deref [_]
        @internalstore)

      (-read [_ key]
        (p/promise
         (fn [deliver]
           (if (nil? key)
             (let [key (codecs/bytes->safebase64 (nonce/random-nonce 48))]
               (deliver [key {}]))
             (let [data (get @internalstore key nil)]
               (if (nil? data)
                 (let [key (codecs/bytes->safebase64 (nonce/random-nonce 48))]
                   (deliver [key {}]))
                 (deliver [key data])))))))

      (-write [_ key data]
        (p/promise
         (fn [deliver]
           (swap! internalstore assoc key data)
           (deliver key))))

      (-delete [_ key]
        (p/promise
         (fn [deliver]
           (swap! internalstore dissoc key)
           (deliver key)))))))

(defn lookup-storage
  "A helper for create session storages with
  helpfull shortcuts."
  {:no-doc true}
  [storage]
  (case storage
    :inmemory (memory-storage)
    ;; :signed-cookie (cookie-storage)
    (if (not (satisfies? ISessionStorage storage))
      (throw (IllegalArgumentException.
              "storage should satisfy ISessionStorage protocol."))
      storage)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- make-cookie
  [value {:keys [cookie-secure cookie-http-only cookie-domain cookie-path]
          :or {cookie-secure false
               cookie-http-only true
               cookie-domain nil
               cookie-path "/"}}]
  {:value value
   :path cookie-path
   :domain cookie-domain
   :secure cookie-secure
   :http-only cookie-http-only})

(defn- context->session
  [context {:keys [storage cookie-name]
            :or {cookie-name default-cookie-name}}]
  (let [^Context ctx (:catacumba/context context)
        cookies (ct/get-cookies context)
        cookie (get cookies (keyword cookie-name) nil)
        sid (:value cookie)]
    (if sid
      (-> (read-session storage sid)
          (p/then (fn [v] [sid (->session sid v)])))
      (let [sid (codecs/bytes->safebase64 (nonce/random-nonce 48))]
        (p/resolved [sid (->session sid)])))))

(defn session
  "A session chain handler constructor."
  ([] (session {}))
  ([{:keys [storage cookie-name]
     :or {storage :inmemory cookie-name default-cookie-name}
     :as options}]
   (let [storage (lookup-storage storage)
         options (assoc options :storage storage)]
     (fn [context]
       (-> (context->session context options)
           (p/then (fn [[sid session]]
                     (ct/before-send context (fn [^Response response]
                                               (cond
                                                 (empty? session)
                                                 (let [cookie (-> (make-cookie sid options)
                                                                  (assoc :max-age 0))]
                                                   (ct/set-cookies! context {cookie-name cookie}))

                                                 (modified? session)
                                                 (let [cookie (make-cookie sid options)]
                                                   (write-session storage sid @session)
                                                   (ct/set-cookies! context {cookie-name cookie})))))
                     (ct/delegate {:session session}))))))))
