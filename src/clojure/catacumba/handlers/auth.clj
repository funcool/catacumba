(ns catacumba.handlers.auth
  "Authentication and Authorization facilities for catacumba
  using funcool/buddy "
  (:require [catacumba.handlers.core :refer [hydrate-context]]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.routing :as routing]
            [catacumba.impl.context :as context]
            [catacumba.impl.helpers :as helpers]
            [catacumba.impl.types]
            [catacumba.impl.http]
            [buddy.sign.jws :as jws]
            [buddy.sign.jwe :as jwe]
            [slingshot.slingshot :refer [try+]]
            [futura.promise :as p])
  (:import catacumba.impl.types.DefaultContext
           catacumba.impl.http.Response
           ratpack.exec.Fulfiller
           ratpack.exec.Promise
           ratpack.handling.Chain
           ratpack.handling.Handler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; External protocols implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IAuthentication
  "Protocol that defines unfied workflow steps for
  all authentication backends."
  (^:private parse [_ request]
    "Parse token (from cookie, session or any other
    http header) and return it.")
  (^:private authenticate [_ context token]
    "Given a request and parsed data (from previous step), tries
    authenticate the current request and return the user entity.
    This function should return a IPromise instance."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- find-header
  "Looks up a header in a headers map case insensitively,
  returning the header map entry, or nil if not present."
  [headers ^String header-name]
  (first (filter #(.equalsIgnoreCase header-name (key %)) headers)))

(defn- get-header
  [request header-name]
  (some-> (:headers request) (find-header header-name) val))

(defn- parse-authorization-header
  [request token-name]
  (some->> (get-header request "authorization")
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
    (parse [_ context]
      (parse-authorization-header context token-name))
    (authenticate [_ context token]
      (p/promise (fn [resolve]
                   (try+
                    (resolve (jws/unsign token secret options))
                    (catch [:type :validation] e
                      (if (fn? on-error)
                        (resolve (on-error context e))
                        (resolve nil)))))))))

(defn jwe-backend
  "The JWS (Json Web Signature) based backend constructor."
  [{:keys [secret options token-name on-error]
    :or {token-name "Token"}}]
  (reify
    IAuthentication
    (parse [_ context]
      (parse-authorization-header context token-name))
    (authenticate [_ context token]
      (p/promise (fn [resolve]
                   (try+
                    (resolve (jwe/decrypt token secret options))
                    (catch [:type :validation] e
                      (if (fn? on-error)
                        (resolve (on-error context e))
                        (resolve nil)))))))))

(defn session-backend
  "Given some options, create a new instance
  of Session backend and return it."
  []
  (reify
    IAuthentication
    (parse [_ context]
      (:identity @(:session context)))
    (authenticate [_ context data]
      data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- do-auth
  "Perform an asynchronous recursive loop over all
  provided backends and tries to authenticate with
  all them in order."
  [context backends ^Fulfiller ff]
  (if-let [backend (first backends)]
    (let [last? (empty? (rest backends))
          token (parse backend context)]
      (if (and (nil? token) last?)
        (.success ff {})
        (-> (authenticate backend context token)
            (p/then (fn [ue]
                      (if (nil? ue)
                        (do-auth context (rest backends) ff)
                        (.success ff {:identity ue}))))
            (p/catch (fn [e]
                       ;; TODO: add error logging
                       (.success ff {}))))))
    (.success ff {})))

(defn auth
  "Authentication chain handler constructor."
  [& backends]
  {:pre [(pos? (count backends))]}
  (fn [context]
    (let [context (hydrate-context context)]
      (-> (:catacumba/context context)
          (helpers/promise #(do-auth context backends %))
          (p/then #(context/delegate context %))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod routing/attach-route :auth
  [^Chain chain [_ & backends]]
  (let [^Handler handler (-> (apply auth backends)
                             (handlers/adapter))]
    (.handler chain handler)))
