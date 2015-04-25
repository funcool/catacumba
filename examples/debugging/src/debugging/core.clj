(ns debugging.core
  (:require [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.impl.handlers :refer [build-request]]
            [prone.stacks :refer [normalize-exception]]
            [prone.debug :as debug]
            [prone.prep :refer [prep-error-page prep-debug-page]]
            [prone.hiccough :refer [render]]
            [prone.middleware :as pronemw])
  (:gen-class))

(defn prone-error-handler
  [appname]
  (let [render-page #'pronemw/render-page
        serve #'pronemw/serve]
    (fn [context throwable]
      [context throwable]
      (.printStackTrace throwable)
      (-> (normalize-exception throwable)
          (prep-error-page [] (build-request (:request context)) [appname])
          (render-page)
          (serve)))))

(defn index
  [context]
  (http/ok "<a href='/somepage'>Go here</a>"
           {:content-type "text/html; encoding=utg-8"}))

(defn some-page
  [context]
  (throw (ex-info "Error" {:some "data"})))

(def app
  (ct/routes [[:error (prone-error-handler "debugging")]
              [:get index]
              [:get "somepage" some-page]]))

(defn -main
  [& args]
  (ct/run-server app))
