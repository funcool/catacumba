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

(ns catacumba.impl.context
  "Functions and helpers for work in a clojure
  way with ratpack types."
  (:require [catacumba.helpers :as hp])
  (:import ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.handling.RequestOutcome
           ratpack.form.Form
           ratpack.parse.Parse
           ratpack.http.Request
           ratpack.http.Response
           ratpack.http.Headers
           ratpack.http.TypedData
           ratpack.http.MutableHeaders
           ratpack.util.MultiValueMap
           ratpack.server.PublicAddress
           ratpack.registry.Registry
           java.util.Optional))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DefaultContext [])
(defrecord ContextData [payload])

(alter-meta! #'->DefaultContext assoc :private true)
(alter-meta! #'map->DefaultContext assoc :private true)
(alter-meta! #'->ContextData assoc :private true)
(alter-meta! #'map->ContextData assoc :private true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn context
  "A catacumba context constructor."
  {:internal true :no-doc true}
  [data]
  (map->DefaultContext data))

(defn get-context-params*
  {:internal true :no-doc true}
  [^Context ctx]
  (let [^Optional odata (.maybeGet ctx ContextData)]
    (if (.isPresent odata)
      (:payload (.get odata))
      {})))

(defn get-context-params
  "Get the current context params.

  The current params can be passed to the next
  handler using the `delegate` function. Is a simple
  way to communicate the handlers chain."
  [^DefaultContext context]
  (get-context-params* (:catacumba/context context)))

(def ^{:no-doc true :internal true}
  +empty-ctxdata+ (ContextData. nil))

(defn delegate
  "Delegate handling to the next handler in line.

  This function accept an additiona parameter for
  pass context parameters to the next handlers, and
  that can be obtained with `context-params`
  function."
  ([]
   +empty-ctxdata+)
  ([data]
   (ContextData. data)))

(defn public-address
  "Get the current public address as URI instance.

  The default implementation uses a variety of strategies to
  attempt to provide the desired result most of the time.
  Information used includes:

  - Configured public address URI (optional)
  - X-Forwarded-Host header (if included in request)
  - X-Forwarded-Proto or X-Forwarded-Ssl headers (if included in request)
  - Absolute request URI (if included in request)
  - Host header (if included in request)
  - Service's bind address and scheme (http vs. https)"
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^PublicAddress addr (.get ctx PublicAddress)]
    (.get addr ctx)))

(defn on-close
  "Register a callback in the context that will be called
  when the connection with the client is closed."
  [^DefaultContext context callback]
  (let [^Context ctx (:catacumba/context context)]
    (.onClose ctx (hp/fn->action callback))))

(defn before-send
  "Register a callback in the context that will be called
  just before send the response to the client. Is a useful
  hook for set some additional cookies, headers or similar
  response transformations."
  [^DefaultContext context callback]
  (let [^Response response (:catacumba/response context)]
    (.beforeSend response (hp/fn->action callback))))

;; Implementation notes:
;; Reflection is used for access to private field of Form instance
;; because ratpack does not allows an other way for iter over
;; all parameters (including files) in an uniform way.

(defn- extract-files
  [^Form form]
  (let [field (.. form getClass (getDeclaredField "files"))]
    (.setAccessible field true)
    (.get field form)))

(defn get-query-params*
  "Parse query params from request and return a
  maybe multivalue map."
  {:internal :true :no-doc true}
  [^Request request]
  (let [^MultiValueMap params (.getQueryParams request)]
    (persistent!
     (reduce (fn [acc key]
               (let [values (.getAll params key)]
                 (reduce #(hp/assoc-conj! %1 (keyword key) %2) acc values)))
             (transient {})
             (.keySet params)))))

(defn get-query-params
  "Parse query params from context and return a
  maybe multivalue map."
  [^DefaultContext context]
  (get-query-params* (:catacumba/request context)))

(defn get-route-params*
  "Return a hash-map with parameters extracted from
  routing patterns."
  [^Context ctx]
  (into {} hp/keywordice-keys-t (.getAllPathTokens ctx)))

(defn get-route-params
  "Return a hash-map with parameters extracted from
  routing patterns."
  [^DefaultContext context]
  (get-route-params* (:catacumba/context context)))

(defn headers->map [^MultiValueMap headers keywordize]
  (persistent!
     (reduce (fn [acc ^String key]
               (let [values (.getAll headers key)
                     key (if keywordize
                           (keyword (.toLowerCase key))
                           (.toLowerCase key))]
                 (reduce #(hp/assoc-conj! %1 key %2) acc values)))
             (transient {})
             (.keySet headers))))

(defn get-headers*
  [^Request request keywordize]
  (let [^Headers headers (.getHeaders request)]
    (headers->map (.asMultiValueMap headers) keywordize)))

(defn get-headers
  [^DefaultContext context]
  (get-headers* (:catacumba/request context) true))

(defn set-headers!
  [^DefaultContext context headers]
  (let [^Response response (:catacumba/response context)
        ^MutableHeaders headersmap (.getHeaders response)]
    (loop [headers headers]
      (when-let [[key vals] (first headers)]
        (.set headersmap (name key) vals)
        (recur (rest headers))))))

(defn- cookie->map
  [cookie]
  {:path (.path cookie)
   :value (.value cookie)
   :domain (.domain cookie)
   :http-only (.isHttpOnly cookie)
   :secure (.isSecure cookie)
   :max-age (.maxAge cookie)})

(defn get-cookies*
  "Get the incoming cookies."
  {:internal true :no-doc true}
  [^Request request]
  (persistent!
   (reduce (fn [acc cookie]
             (let [name (keyword (.name cookie))]
               (assoc! acc name (cookie->map cookie))))
           (transient {})
           (into [] (.getCookies request)))))

(defn get-cookies
  "Get the incoming cookies."
  [^DefaultContext context]
  (get-cookies* (:catacumba/request context)))

(defn set-status!
  "Set the response http status."
  [context status]
  (let [^Response response (:catacumba/response context)]
    (.status response status)))

(defn set-cookies!
  "Set the outgoing cookies.

      (set-cookies! ctx {:cookiename {:value \"value\"}})

  As well as setting the value of the cookie,
  you can also set additional attributes:

  - `:domain` - restrict the cookie to a specific domain
  - `:path` - restrict the cookie to a specific path
  - `:secure` - restrict the cookie to HTTPS URLs if true
  - `:http-only` - restrict the cookie to HTTP if true
                   (not accessible via e.g. JavaScript)
  - `:max-age` - the number of seconds until the cookie expires

  As you can observe is almost identical hash map structure
  as used in the ring especification."
  [^DefaultContext context cookies]
  ;; TODO: remove nesed blocks using properly the reduce.
  (let [^Response response (:catacumba/response context)]
    (loop [cookies (into [] cookies)]
      (when-let [[cookiename cookiedata] (first cookies)]
        (let [cookie (.cookie response (name cookiename) "")]
          (reduce (fn [_ [k v]]
                    (case k
                      :path (.setPath cookie v)
                      :domain (.setDomain cookie v)
                      :secure (.setSecure cookie v)
                      :http-only (.setHttpOnly cookie v)
                      :max-age (.setMaxAge cookie v)
                      :value (.setValue cookie v)))
                  nil
                (into [] cookiedata))
          (recur (rest cookies)))))))

(defn get-formdata*
  [^Context ctx ^TypedData body]
  (let [^Form form (.parse ctx body (Parse/of Form))
        ^MultiValueMap files (.files form)
        result (transient {})]
    (reduce (fn [acc key]
              (let [values (.getAll files key)]
                (reduce #(hp/assoc-conj! %1 (keyword key) %2) acc values)))
            result
            (.keySet files))
    (reduce (fn [acc key]
              (let [values (.getAll form key)]
                (reduce #(hp/assoc-conj! %1 (keyword key) %2) acc values)))
            result
            (.keySet form))
      (persistent! result)))

(defn get-formdata
  [^DefaultContext context]
  (get-formdata* (:catacumba/context context)
                 (:body context)))

(defn resolve-file
  "Resolve file using the current filesystem binding
  configuration. The path will be resolved as relative
  to the filesystem binding root."
  [^DefaultContext context ^String path]
  {:pre [(string? path)]}
  (let [^Context ctx (:catacumba/context context)]
    (.file ctx path)))
