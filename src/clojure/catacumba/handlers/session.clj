(ns catacumba.handlers.session
  "Sessions support for catacumba.

  WARNING: this is still work in progress and
  the api will be changed in the future."
  (:refer-clojure :exclude [empty?])
  (:require [futura.atomic :as atomic]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codecs]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.context :as context])
  (:import clojure.lang.IAtom
           clojure.lang.IDeref
           clojure.lang.Counted
           clojure.lang.IFn
           clojure.lang.ISeq
           ratpack.http.ResponseMetaData))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const default-cookie-name :sessionid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Abstractions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISession
  (^:private empty? [_] "Check if the session is empty.")
  (^:private accessed? [_] "Check if session is accessed")
  (^:private modified? [_] "Check if session is modified"))

(defprotocol ISessionStorage
  (^:private read-session [_ key] "")
  (^:private write-session [_ key data] "")
  (^:private delete-session [_ key] ""))

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
  (empty? [_] (= (count @data) 0))
  (accessed? [_] @accessed)
  (modified? [_] @modified))

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
      (read-session [_ key]
        (let [key (keyword key)]
          (get @internalstore key nil)))
      (write-session [_ key data]
        (let [key (keyword key)]
          (swap! internalstore assoc key data)))
      (delete-session [_ key]
        (let [key (keyword key)]
          (swap! internalstore dissoc key))))))

(defn lookup-storage
  "A helper for create session storages with
  helpfull shortcuts."
  {:no-doc true}
  [storage]
  (case storage
    :inmemory (memory-storage)
    ;; :signed-cookie (cookie-storage)
    (if (not (satisfies? ISessionStorage storage))
      (throw (IllegalArgumentException. "storage should satisfy ISessionStorage protocol."))
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
  (let [cookies (handlers/get-cookies context)
        cookie (get cookies (keyword cookie-name) nil)
        sid (:value cookie)]
    (if sid
      (let [data (read-session storage sid)]
        [sid (->session sid data)])
      (let [sid (codecs/bytes->safebase64 (nonce/random-nonce 48))]
        [sid (->session sid)]))))

(defn session
  ([] (session {}))
  ([{:keys [storage cookie-name]
     :or {storage :inmemory cookie-name default-cookie-name}
     :as options}]
   (let [storage (lookup-storage storage)
         options (assoc options :storage storage)]
     (fn [context]
       (let [[sid session] (context->session context options)]
         (context/before-send context (fn [^ResponseMetaData response]
                                        (cond
                                          (empty? session)
                                          (let [cookie (-> (make-cookie sid options)
                                                           (assoc :max-age 0))]
                                            (handlers/set-cookies! context {cookie-name cookie}))

                                          (modified? session)
                                          (let [cookie (make-cookie sid options)]
                                            (write-session storage sid @session)
                                            (handlers/set-cookies! context {cookie-name cookie})))))
         (context/delegate context {:session session}))))))
