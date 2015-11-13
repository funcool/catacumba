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

(ns catacumba.handlers.misc
  (:require [cuerdas.core :as str]
            [ns-tracker.core :refer [ns-tracker]]
            [catacumba.core :refer [on-close]]
            [catacumba.impl.routing :as routing]
            [catacumba.impl.context :as ct]
            [catacumba.impl.handlers :as hs])
  (:import ratpack.handling.RequestLogger
           ratpack.handling.RequestOutcome
           ratpack.handling.Chain
           ratpack.handling.Context
           ratpack.handling.Handler
           ratpack.exec.Execution
           ratpack.http.Status
           ratpack.func.Block
           ratpack.exec.ExecInterceptor
           ratpack.exec.ExecInterceptor$ExecType))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CORS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- allow-origin?
  [value {:keys [origin]}]
  (cond
    (nil? value) value
    (= origin "*") origin
    (set? origin) (origin value)
    (= origin value) origin))

(defn- handle-preflight
  [context headers {:keys [allow-methods allow-headers max-age allow-credentials] :as opts}]
  (let [^String origin (get headers :origin)]
    (when-let [origin (allow-origin? origin opts)]
      (ct/set-headers! context {:access-control-allow-origin origin
                                :access-control-allow-methods (str/join "," allow-methods)})
      (when allow-credentials
        (ct/set-headers! context {:access-control-allow-credentials true}))
      (when max-age
        (ct/set-headers! context {:access-control-max-age max-age}))
      (when allow-headers
        (ct/set-headers! context {:access-control-allow-headers (str/join "," allow-headers)})))
    (hs/send! context "")))

(defn- handle-response
  [context headers {:keys [allow-headers expose-headers origin] :as opts}]
  (let [^String origin (get headers :origin)]
    (when-let [origin (allow-origin? origin opts)]
      (ct/set-headers! context {:access-control-allow-origin origin})
      (when allow-headers
        (ct/set-headers! context {:access-control-allow-headers (str/join "," allow-headers)}))
      (when expose-headers
        (ct/set-headers! context {:access-control-expose-headers (str/join "," expose-headers)})))
    (ct/delegate)))

(defn- cors-preflight?
  [context headers]
  (and (= (:method context) :options)
       (contains? headers :origin)
       (contains? headers :access-control-request-method)))

(defn cors
  "A chain handler that handles cors related headers."
  [{:keys [origin] :as opts}]
  (fn [context]
    (let [headers (:headers context)]
      (if (cors-preflight? context headers)
        (handle-preflight context headers opts)
        (handle-response context headers opts)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Autorealoader
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn autoreloader
  ([] (autoreloader {}))
  ([{:keys [dirs] :or {dirs ["src"]}}]
   (let [tracker (ns-tracker dirs)]
     (fn [context]
       (doseq [ns-sym (tracker)]
         (println "=> reload:" ns-sym)
         (require ns-sym :reload))
       (ct/delegate)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logging
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- status->map [^Status status]
  {:code (.getCode status)
   :message (.getMessage status)})

(defn- outcome->map [^RequestOutcome outcome]
  (let [response (.getResponse outcome)]
    {:headers  (ct/headers->map
                (.. response getHeaders asMultiValueMap)
                true)
     :status   (status->map (.getStatus response))
     :sent-at  (.getSentAt outcome)
     :duration (.getDuration outcome)}))

(defn log
  ([] (RequestLogger/ncsa))
  ([log-fn]
   (fn [context]
     (on-close context #(log-fn context (outcome->map %)))
     (ct/delegate))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- exec-interceptor
  [interceptor]
  (reify ExecInterceptor
    (^void intercept [_ ^Execution exc ^ExecInterceptor$ExecType t ^Block b]
      (let [continuation #(.execute b)
            exectype (if (= t ExecInterceptor$ExecType/BLOCKING)
                       :blocking
                       :compute)]
        (interceptor exc exectype continuation)))))

(defn interceptor
  "Start interceptor from current context.

  It wraps the rest of route chain the execution. It receive a
  continuation (as a cloure function) that must be called in
  order for processing to proceed."
  [context interceptor]
  (let [^Context ctx (:catacumba/context context)
        ^Execution exec (.getExecution ctx)]
    (.addInterceptor exec
                     (exec-interceptor interceptor)
                     (reify Block
                       (^void execute [_]
                         (.next ctx))))))

(defmethod routing/attach-route :interceptor
  [^Chain chain [_ interceptor']]
  (let [handler #(interceptor % interceptor')]
    (.all chain ^Handler (hs/adapter handler))))

