(ns echo-websocket.core
  (:require [clojure.core.async :as async :refer [go-loop <! >! close!]]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [catacumba.core :as ct]
            [catacumba.http :as http]))

(defn echo-handler
  "This is my echo handler that serves as
  a websocket handler example."
  {:handler-type :catacumba/websocket}
  [{:keys [in out]}]
  (go-loop []
    (if-let [received (<! in)]
      (do
        (>! out received)
        (recur))
      (close! out))))

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server #'echo-handler))
