(ns websocket-echo.core
  (:require [clojure.core.async :as async :refer [go-loop <! >! close!]]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [catacumba.core :as ct]
            [catacumba.handlers.misc :refer (autoreloader)]
            [catacumba.http :as http]))

(defn home-handler
  [context]
  (-> (slurp (io/resource "index.html"))
      (http/ok {:content-type "text/html;charset=utf-8"})))

(defn websocket-handler
  "This is my echo handler that serves as
  a websocket handler example."
  {:handler-type :catacumba/websocket}
  [{:keys [in out]}]
  (go-loop []
    (if-let [received (<! in)]
      (do
        (>! out received)
        (recur))
      (do
        (println "closing")
        (close! out)))))

(def app
  (ct/routes [[:any (autoreloader)]
              [:get "ws" #'websocket-handler]
              [:get #'home-handler]]))

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server app {:debug true}))
