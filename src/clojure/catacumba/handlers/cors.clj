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
