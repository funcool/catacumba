;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
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
  (:require [catacumba.impl.helpers :as hp]
            [promesa.core :as p])
  (:import catacumba.impl.DelegatedContext
           catacumba.impl.ContextHolder
           ratpack.handling.Handler
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
           io.netty.handler.codec.http.cookie.Cookie
           java.util.Optional))

;; --- Helpers

(defn- assoc-conj!
  {:internal true :no-doc true}
  [the-map key val]
  (assoc! the-map key
          (if-let [cur (get the-map key)]
            (if (vector? cur)
              (conj cur val)
              [cur val])
            val)))

(defn get-response*
  {:internal true}
  [context]
  (cond
    (instance? Response context) context
    (instance? Context context) (.getResponse context)
    (map? context) (:catacumba/response context)
    :else (throw (ex-info "Invalid arguments2" {}))))

(defn get-context*
  {:internal true}
  [context]
  (cond
    (instance? Context context) context
    (map? context) (:catacumba/context context)
    :else (throw (ex-info "Invalid arguments1" {}))))

(defn get-request*
  {:internal true}
  [context]
  (cond
    (instance? Request context) context
    (instance? Context context) (.getRequest context)
    (map? context) (:catacumba/request context)
    :else (throw (ex-info "Invalid arguments3" {}))))

;; --- Public Api

(defn get-context-params
  "Get the current context params.

  The current params can be passed to the next
  handler using the `delegate` function. Is a simple
  way to communicate the handlers chain."
  {:internal true :no-doc true}
  [ctx]
  (when-let [dc (-> (get-context* ctx)
                    (hp/maybe-get DelegatedContext))]
    (.-data dc)))

(defn delegate
  "Delegate handling to the next handler in line.

  This function accept an additiona parameter for
  pass context parameters to the next handlers, and
  that can be obtained with `context-params`
  function."
  ([] (DelegatedContext. nil))
  ([data] (DelegatedContext. data)))

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
  [context]
  (let [^Context ctx (get-context* context)
        ^PublicAddress addr (.get ctx PublicAddress)]
    (.get addr ctx)))

(defn on-close
  "Register a callback in the context that will be called
  when the connection with the client is closed."
  [context callback]
  (let [^Context ctx (:catacumba/context context)]
    (.onClose ctx (hp/fn->action callback))))

(defn before-send
  "Register a callback in the context that will be called
  just before send the response to the client. Is a useful
  hook for set some additional cookies, headers or similar
  response transformations."
  [context callback]
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

(defn get-query-params
  "Parse query params from request and return a
  maybe multivalue map."
  [context]
  (let [^Request request (get-request* context)
        ^MultiValueMap params (.getQueryParams request)]
    (persistent!
     (reduce (fn [acc key]
               (let [values (.getAll params key)]
                 (reduce #(assoc-conj! %1 (keyword key) %2) acc values)))
             (transient {})
             (.keySet params)))))

(defn get-route-params
  "Return a hash-map with parameters extracted from
  routing patterns."
  [context]
  (let [^Context ctx (get-context* context)]
    (into {} hp/keywordice-keys-t (.getAllPathTokens ctx))))

(defn headers->map
  {:internal true :no-doc true}
  [^MultiValueMap headers keywordize]
  (persistent!
   (reduce (fn [acc ^String key]
             (let [values (.getAll headers key)
                   key (if keywordize
                           (keyword (.toLowerCase key))
                           (.toLowerCase key))]
               (reduce #(assoc-conj! %1 key %2) acc values)))
           (transient {})
           (.keySet headers))))

(defn get-headers
  ([context] (get-headers context true))
  ([context keywordize?]
   (let [^Request request (get-request* context)
         ^Headers headers (.getHeaders request)]
     (headers->map (.asMultiValueMap headers) keywordize?))))

(defn set-headers!
  [context headers]
  (let [^Response response (get-response* context)
        ^MutableHeaders headersmap (.getHeaders response)]
    (loop [headers headers]
      (when-let [[key vals] (first headers)]
        (.set headersmap (name key) vals)
        (recur (rest headers))))))

(defn- cookie->map
  [^Cookie cookie]
  {:path (.path cookie)
   :value (.value cookie)
   :domain (.domain cookie)
   :http-only (.isHttpOnly cookie)
   :secure (.isSecure cookie)
   :max-age (.maxAge cookie)})

(defn get-cookies
  "Get the incoming cookies."
  [context]
  (let [^Request request (get-request* context)]
    (persistent!
     (reduce (fn [acc ^Cookie cookie]
               (let [name (keyword (.name cookie))]
                 (assoc! acc name (cookie->map cookie))))
             (transient {})
             (into [] (.getCookies request))))))

(defn set-status!
  "Set the response http status."
  [context status]
  (let [^Response response (get-response* context)]
    (.status response (int status))))

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
  [context cookies]
  ;; TODO: remove nesed blocks using properly the reduce.
  (let [^Response response (get-response* context)]
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

(defn- parse-form-files
  [^MultiValueMap files result-map]
  (persistent!
    (reduce
      (fn [acc key]
        (let [values (.getAll files key)]
          (reduce #(assoc-conj! %1 (keyword key) %2) acc values)))
      (transient result-map)
      (.keySet files))))

(defn- parse-form-fields
  [^Form form result-map]
  (persistent!
    (reduce
      (fn [acc the-key]
        (let [values (.getAll form the-key)]
          (reduce #(assoc-conj! %1 (keyword the-key) %2) acc values)))
      (transient result-map)
      (.keySet form))))

(defn get-formdata*
  {:internal true}
  [^Context ctx ^TypedData body]
  (let [^Form form (.parse ctx body (Parse/of Form))
        ^MultiValueMap files (.files form)]
    (->> {}
      (parse-form-files files)
      (parse-form-fields form))))

(declare get-body!)

(defn get-formdata
  [{:keys [body] :as context}]
  (let [ctx (get-context* context)]
    (if body
      (p/resolved (get-formdata* ctx body))
      (->> (get-body! ctx)
           (p/map #(get-formdata* ctx %))))))

(defn resolve-file
  "Resolve file using the current filesystem binding
  configuration. The path will be resolved as relative
  to the filesystem binding root."
  [context ^String path]
  (let [^Context ctx (:catacumba/context context)]
    (.file ctx path)))

(defn get-body!
  "Reads asynchronously the body from context. This
  function return a promise (CompletableFuture instance).

  NOTE: it can only be done once, consider using specialized
  body parsing handlers instead of this. This function
  should be considered low-level."
  [context]
  (p/promise
   (fn [resolve reject]
     (let [^Request request (get-request* context)]
       (-> (.getBody request)
           (hp/on-error reject)
           (hp/then resolve))))))

;; --- Impl

(defn build-context
  {:internal true}
  [^Context ctx]
  (let [^Request request (.getRequest ctx)
        ^Response response (.getResponse ctx)]
    {:catacumba/context ctx
     :catacumba/request request
     :catacumba/response response

     :path (str "/" (.getPath request))
     :query (.getQuery request)
     :method (keyword (.. request getMethod getName toLowerCase))
     :query-params (get-query-params request)
     :cookies (get-cookies request)
     :headers (get-headers request true)}))

(defn create-context
  {:internal true}
  [^Context ctx]
  (let [holder (-> (.getRequest ctx)
                   (hp/maybe-get ContextHolder))]
    (merge (if holder
             (.-data ^ContextHolder holder)
             (build-context ctx))
           {:route-params (get-route-params ctx)}
           (get-context-params ctx))))

