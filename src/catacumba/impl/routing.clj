(ns catacumba.impl.routing
  (:require [catacumba.impl.handlers :as handlers]
            [catacumba.impl.helpers :as helpers]
            [catacumba.utils :as utils])
  (:import ratpack.handling.Context
           ratpack.handling.Chain
           ratpack.handling.Handlers
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

(defmethod attach-route :default
  [^Chain chain [method ^String path & handlers]]
  (let [^List handlers (mapv handlers/ratpack-adapter handlers)]
    (condp = method
      :get (.get chain path (Handlers/chain handlers))
      :post (.post chain path (Handlers/chain handlers))
      :put (.put chain path (Handlers/chain handlers))
      :patch (.patch chain path (Handlers/chain handlers))
      :delete (.delete chain path (Handlers/chain handlers))
      :all (.handler chain path (Handlers/chain handlers)))))

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
