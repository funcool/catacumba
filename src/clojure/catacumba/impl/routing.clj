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

(ns catacumba.impl.routing
  (:require [catacumba.impl.handlers :as handlers]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as helpers])
  (:import ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handlers
           ratpack.handling.Handler
           ratpack.handling.ByMethodSpec
           ratpack.file.FileHandlerSpec
           ratpack.error.ServerErrorHandler
           ratpack.registry.RegistrySpec
           ratpack.func.Action
           java.util.List))

(defmulti attach-route
  (fn [chain [method & args]]
    method))

(defmethod attach-route :assets
  [^Chain chain [_ ^String path {:keys [dir indexes]}]]
  (.files chain (helpers/action
                 (fn [^FileHandlerSpec spec]
                   (.path spec path)
                   (when indexes (.index spec (into-array String indexes)))
                   (when dir (.dir spec dir))))))

(defmethod attach-route :prefix
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.prefix chain path ^Action (helpers/action callback))))

(defmethod attach-route :scope
  [^Chain chain [_ & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.insert chain ^Action (helpers/action callback))))

;; TODO: perform handlers adapter on definition time
;; instead on request time, for faster error detection
;; and performance improvements.

;; TODO: reimplement this for better performance.
;; The current implementation creates twice the
;; DefaultContext instace from Context.

(defmethod attach-route :by-method
  [^Chain chain [_ ^String path & handlers]]
  (let [callback (fn [^Context ctx ^ByMethodSpec spec]
                   (run! (fn [[method handler]]
                           (let [handler (handlers/adapter handler)
                                 block (reify ratpack.func.Block
                                         (^void execute [_]
                                           (.handle ^Handler handler ctx)))]
                             (case method
                               :get (.get spec block)
                               :post (.post spec block)
                               :put (.put spec block)
                               :delete (.delete spec block)
                               :patch (.patch spec block))))
                         handlers))
        handler (fn [context]
                  (let [^Context ctx (:catacumba/context context)]
                    (.byMethod ctx (helpers/action (partial callback ctx)))))
        handler (handlers/adapter handler)]
    (.prefix chain path (helpers/action #(.all % handler)))))

(defmethod attach-route :error
  [^Chain chain [_ error-handler]]
  (letfn [(on-register [^RegistrySpec rspec]
            (let [ehandler (reify ServerErrorHandler
                             (error [_ ctx throwable]
                               (let [context (ctx/context ctx)
                                     response (error-handler context throwable)]
                                 (when (satisfies? handlers/IHandlerResponse response)
                                   (handlers/handle-response response context)))))]
              (.add rspec ServerErrorHandler ehandler)))]
    (.register chain ^Action (helpers/action on-register))))

(defmethod attach-route :default
  [chain [method & handlers-and-path]]
  (let [path (first handlers-and-path)]
    (if (string? path)
      (let [^Handler handler (-> (mapv handlers/adapter (rest handlers-and-path))
                                 (Handlers/chain))]
        (case method
          :any (.prefix chain path (helpers/action #(.all % handler)))
          :all (.prefix chain path (helpers/action #(.all % handler)))
          :get (.get chain path handler)
          :post (.post chain path handler)
          :put (.put chain path handler)
          :patch (.patch chain path handler)
          :delete (.delete chain path handler)))
      (let [^Handler handler (-> (mapv handlers/adapter handlers-and-path)
                                 (Handlers/chain))]
        (case method
          :any (.all chain handler)
          :all (.all chain handler)
          :get (.get chain handler)
          :post (.post chain handler)
          :put (.put chain handler)
          :patch (.patch chain handler)
          :delete (.delete chain handler))))))

(defn routes
  "Is a high order function that access a routes vector
  as argument and return a ratpack router type handler."
  [routes]
  (with-meta (fn [chain] (reduce attach-route chain routes))
    {:handler-type :catacumba/router}))
