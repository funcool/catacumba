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

(ns catacumba.impl.routing
  (:require [catacumba.impl.handlers :as hs]
            [catacumba.impl.context :as ct]
            [catacumba.helpers :as hp])
  (:import catacumba.impl.context.DefaultContext
           ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handlers
           ratpack.handling.Handler
           ratpack.handling.ByMethodSpec
           ratpack.func.Block
           ratpack.file.FileHandlerSpec
           ratpack.error.ServerErrorHandler
           ratpack.registry.RegistrySpec
           ratpack.func.Action
           java.util.List))

(defn- combine-handlers
  "Given a list of handlers, return a handler
  that chains them."
  [handlers]
  (Handlers/chain (mapv hs/adapter handlers)))

(defn wrap-handler
  [handler method]
  (let [handler (hs/adapter handler)]
    (reify Handler
      (^void handle [_ ^Context ctx]
       (let [current-method (keyword (.. ctx getRequest getMethod getName toLowerCase))]
         (if (identical? current-method method)
           (.insert ctx (into-array Handler [handler]))
           (.next ctx)))))))

(defmulti attach-route
  (fn [chain [method & args]]
    method))

(defmethod attach-route :assets
  [^Chain chain [_ ^String path {:keys [dir indexes index]}]]
  (.files chain (hp/fn->action
                 (fn [^FileHandlerSpec spec]
                   (when-not (empty? path)
                     (.path spec path))
                   (when index
                     (.indexFiles spec (into-array String [index])))
                   (when indexes
                     (.indexFiles spec (into-array String indexes)))
                   (when dir
                     (.dir spec dir))))))

(defmethod attach-route :prefix
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.prefix chain path ^Action (hp/fn->action callback))))

(defmethod attach-route :scope
  [^Chain chain [_ & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.insert chain ^Action (hp/fn->action callback))))

(defmethod attach-route :by-method
  [^Chain chain [_ handlersmap]]
  {:pre [(map? handlersmap)]}
  (letfn [(attach [^Context ctx ^ByMethodSpec spec [method handler]]
            (let [^Handler handler (if (vector? handler) (combine-handlers handler) (hs/adapter handler))
                  ^Block block (hp/fn->block #(.handle handler ctx))]
              (case method
                :get (.get spec block)
                :post (.post spec block)
                :put (.put spec block)
                :delete (.delete spec block)
                :patch (.patch spec block))))
          (callback [ctx ^ByMethodSpec spec]
            (let [attach' (partial attach ctx spec)]
              (run! attach' handlersmap)))]
    (.all chain (reify Handler
                  (^void handle [_ ^Context ctx]
                    (.byMethod ctx (hp/fn->action (partial callback ctx))))))))

(defmethod attach-route :error
  [^Chain chain [_ error-handler]]
  (letfn [(on-register [^RegistrySpec rspec]
            (let [ehandler (reify ServerErrorHandler
                             (error [_ ctx throwable]
                               (hs/hydrate-context ctx (fn [^DefaultContext context]
                                                         (let [response (error-handler context throwable)]
                                                           (when (satisfies? hs/IHandlerResponse response)
                                                             (hs/-handle-response response context)))))))]
              (.add rspec ServerErrorHandler ehandler)))]
    (.register chain ^Action (hp/fn->action on-register))))

(defmethod attach-route :setup
  [^Chain chain [_ ^String path setup]]
  (.prefix chain path (hp/fn->action setup)))

(defn- split-path-from-handlers
  [handlers]
  (if (string? (first handlers))
    [(first handlers) (rest handlers)]
    [nil handlers]))

(defmethod attach-route :any
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (combine-handlers handlers)]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :all
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (combine-handlers handlers)]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :get
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (-> (combine-handlers handlers)
                    (wrap-handler method))]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :post
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (-> (combine-handlers handlers)
                    (wrap-handler method))]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :put
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (-> (combine-handlers handlers)
                    (wrap-handler method))]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :patch
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (-> (combine-handlers handlers)
                    (wrap-handler method))]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defmethod attach-route :delete
  [^Chain chain [method & handlers]]
  (let [[path handlers] (split-path-from-handlers handlers)
        handler (-> (combine-handlers handlers)
                    (wrap-handler method))]
    (if (nil? path)
      (.all chain ^Handler handler)
      (.path chain path ^Handler handler))))

(defn routes
  "Is a high order function that access a routes vector
  as argument and return a ratpack router type handler."
  [routes]
  (with-meta (fn [chain] (reduce attach-route chain routes))
    {:handler-type :catacumba/router}))
