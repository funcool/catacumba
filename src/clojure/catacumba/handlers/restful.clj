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

(ns catacumba.handlers.restful
  (:require [catacumba.impl.handlers :as hs]
            [catacumba.impl.routing :as rt]
            [catacumba.http :as http]
            [catacumba.impl.helpers :as hp])
  (:import ratpack.handling.Handlers
           ratpack.handling.Handler
           ratpack.handling.Context
           ratpack.handling.ByMethodSpec
           ratpack.handling.Chain
           ratpack.func.Block
           ratpack.registry.Registry))

(defn- noop-handler
  [context]
  (http/method-not-allowed ""))

(defn- attach-handler
  [ctx rsh ^ByMethodSpec spec method]
  (let [^Handler handler (hs/adapter (or (get rsh method) noop-handler))
        ^Block block (hp/fn->block #(.handle handler ctx))]
    (case method
      :show (.get spec block)
      :create (.post spec block)
      :update (.put spec block)
      :delete (.delete spec block))))

(defn- main-handler
  [rsh]
  (reify Handler
    (^void handle [_ ^Context ctx]
      (.byMethod ctx (hp/fn->action
                      (fn [^ByMethodSpec spec]
                        (run! #(attach-handler ctx rsh spec %)
                              [:show :create :update :delete])))))))


(defn- resource->routes
  [resource]
  [[:prefix ":id"
    [:by-method {:get (or (get resource :show) noop-handler)
                 :put (or (get resource :update) noop-handler)
                 :delete (or (get resource :delete) noop-handler)}]]
   [:by-method {:get (or (get resource :index) noop-handler)
                :post (or (get resource :create) noop-handler)}]])

(defn- resource->setup
  [rsh]
  (let [routes (resource->routes rsh)]
    (fn [^Chain chain]
      (reduce rt/attach-route chain routes))))

(defmethod rt/attach-route :restful/resource
  [^Chain chain [_ ^String path resource]]
  (let [callback (resource->setup resource)]
    (.prefix chain path (hp/fn->action callback))))


