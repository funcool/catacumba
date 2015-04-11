(ns catacumba.impl.routing
  (:require [catacumba.impl.handlers :as handlers]
            [catacumba.impl.helpers :as helpers]
            [catacumba.utils :as utils])
  (:import ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handlers
           ratpack.handling.Handler
           ratpack.error.ServerErrorHandler
           ratpack.registry.RegistrySpec
           ratpack.func.Action
           java.util.List))

(defmulti attach-route
  (fn [chain [method & _]] method))

(defmethod attach-route :assets
  [^Chain chain [_ ^String path & indexes]]
  (let [indexes (into-array String indexes)]
    (.assets chain path indexes)))

(defmethod attach-route :prefix
  [^Chain chain [_ ^String path & handlers]]
  (let [callback #(reduce attach-route % handlers)]
    (.prefix chain path ^Action (helpers/action callback))))

(defmethod attach-route :error
  [^Chain chain [_ error-handler]]
  (letfn [(on-register [^RegistrySpec rspec]
            (let [ehandler (reify ServerErrorHandler
                             (error [_ context throwable]
                               (let [response (error-handler context throwable)]
                                 (when (satisfies? handlers/IHandlerResponse response)
                                   (handlers/handle-response response context)))))]
              (.add rspec ServerErrorHandler ehandler)))]
    (.register chain ^Action (helpers/action on-register))))

(defmethod attach-route :default
  [^Chain chain [method & handlers-and-path]]
  (let [path (first handlers-and-path)]
    (if (string? path)
      (let [^Handler handler (-> (map handlers/ratpack-adapter (rest handlers-and-path))
                                 (Handlers/chain))]
        (case method
          :all (.handler chain path handler)
          :get (.get chain path handler)
          :post (.post chain path handler)
          :put (.put chain path handler)
          :patch (.patch chain path handler)
          :delete (.delete chain path handler)))
      (let [^Handler handler (-> (map handlers/ratpack-adapter handlers-and-path)
                                (Handlers/chain))]
        (case method
          :all (.handler chain handler)
          :get (.get chain handler)
          :post (.post chain handler)
          :put (.put chain handler)
          :patch (.patch chain handler)
          :delete (.delete chain handler))))))

(defn routes
  "Is a high order function that access a routes vector
  as argument and return a ratpack router type handler."
  [routes]
  (with-meta
    (fn [chain] (reduce attach-route chain routes))
    {:type :ratpack-router}))

(defn route-params
  "Return a hash-map with parameters extracted from
  routing patterns."
  [^Context context]
  (into {} utils/keywordice-keys-t (.getPathTokens context)))
