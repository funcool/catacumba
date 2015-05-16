(ns catacumba.handlers.auth
  "Authentication and Authorization facilities for catacumba
  using funcool/buddy-auth."
  (:require [buddy.auth.http :as buddy-http]
            [buddy.auth.protocols :as buddy-proto]
            [catacumba.handlers.core :refer [hydrate-context]]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.routing :as routing]
            [catacumba.impl.context :as context]
            [catacumba.impl.types]
            [catacumba.impl.http])
  (:import catacumba.impl.types.DefaultContext
           catacumba.impl.http.Response
           ratpack.handling.Chain
           ratpack.handling.Handler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; External protocols implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (extend-protocol buddy-http/IRequest
;;   DefaultContext
;;   (get-header [request name]
;;     ;; implementation
;;     ))

(extend-protocol buddy-http/IResponse
  Response
  (response? [_] true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Builtin backends
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn session-backend
  "Given some options, create a new instance
  of Session backend and return it."
  []
  (reify
    buddy-proto/IAuthentication
    (parse [_ context]
      (:identity @(:session context)))
    (authenticate [_ context data]
      (assoc context :identity data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn auth
  "Authentication chain handler constructor."
  [& backends]
  {:pre [(pos? (count backends))]}
  (fn [context]
    (let [context (hydrate-context context)]
      (loop [[current & pending] backends]
        (let [last? (empty? pending)
              context (assoc context :auth-backend current)
              resp (buddy-proto/parse current context)]
          (if (and (buddy-http/response? resp) last?)
            resp
            (if (and (nil? resp) last?)
              (context/delegate context)
              (let [resp (buddy-proto/authenticate current context resp)]
                (if (and (buddy-http/response? resp) last?)
                  resp
                  (if (or (:identity resp) last?)
                    (context/delegate context resp)
                    (recur pending)))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod routing/attach-route :auth
  [^Chain chain [_ & backends]]
  (let [^Handler handler (-> (apply auth backends)
                             (handlers/adapter))]
    (.handler chain handler)))
