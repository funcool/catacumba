(ns catacumba.impl.routing
  (:require [catacumba.impl.handlers :as handlers]
            [catacumba.impl.context :as ctx]
            [catacumba.impl.helpers :as helpers])
  (:import ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handlers
           ratpack.handling.Handler
           ratpack.error.ServerErrorHandler
           ratpack.registry.RegistrySpec
           ratpack.func.Action
           java.util.List))

(defmulti attach-route
  (fn [chain [method & args]]
    method))

(defmethod attach-route :assets
  [^Chain chain [_ ^String path & indexes]]
  (let [indexes (into-array String indexes)]
    (.assets chain path indexes)))

(defmethod attach-route :prefix
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.prefix chain path ^Action (helpers/action callback))))

(defmethod attach-route :insert
  [^Chain chain [_ & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.insert chain ^Action (helpers/action callback))))

;; TODO: perform handlers adapter on definition time
;; instead on request time, for faster error detection
;; and performance improvements.

(defmethod attach-route :by-method
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)
        handler (fn [context]
                  (let [^Context ctx (:catacumba/context context)]
                    (.byMethod ctx (helpers/action callback))))
        handler (handlers/adapter handler)]
    (.handler chain path handler)))

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
      (let [^Handler handler (-> (map handlers/adapter (rest handlers-and-path))
                                 (Handlers/chain))]
        (case method
          :any (.handler chain path handler)
          :get (.get chain path handler)
          :post (.post chain path handler)
          :put (.put chain path handler)
          :patch (.patch chain path handler)
          :delete (.delete chain path handler)))
      (let [^Handler handler (-> (map handlers/adapter handlers-and-path)
                                 (Handlers/chain))]
        (case method
          :any (.handler chain handler)
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
