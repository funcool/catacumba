(ns catacumba.handlers.auth
  "Authentication and Authorization facilities for catacumba
  using funcool/buddy-auth."
  (:require [buddy.auth.http :as buddy-http]
            [buddy.auth.protocols :as buddy-proto]
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

(extend-protocol buddy-http/IRequest
  DefaultContext
  (get-header [request name]
    ;; implementation
    ))

(extend-protocol buddy-http/IResponse
  Response
  (response? [_] true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authentication
  "Authentication chain handler constructor."
  [& backends]
  {:pre [(pos? (count backends))]}
  (fn [context]
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
                  (recur pending))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod routing/attach-route :auth
  [^Chain chain [_ & backends]]
  (let [^Handler handler (-> (apply authentication backends)
                             (handlers/adapter))]
    (.handler chain handler)))
