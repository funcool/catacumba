(ns debugging.core
  (:require [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.helpers :as hp]
            [catacumba.handlers.prone :as prone])
  (:gen-class))

(defn index
  [context]
  (http/ok "<a href='/somepage'>Go here</a>"
           {:content-type "text/html; encoding=utg-8"}))

(defn middle-hander
  [context]
  (ct/delegate))

(defn some-page
  [context]
  (throw (ex-info "Error" {:some "data"})))

(def app
  (ct/routes [[:setup (prone/handler {:app-namespaces ["debugging"]})]
              [:get "somepage" middle-hander some-page]
              [:get index]]))

(defn -main
  [& args]
  (ct/run-server app))
