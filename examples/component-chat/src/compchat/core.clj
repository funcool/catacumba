(ns compchat.core
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.components :as ctcomp])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Event Bus
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IEventBus
  (subscribe [_ channel] "Create a subscription.")
  (send-message [_ message] "Broadcast the message to all subscibers."))

(defrecord EventBus [input bus]
  component/Lifecycle
  (start [component]
    (let [chan (async/chan)]
      (assoc component :input chan :bus (async/mult chan))))

  (stop [component]
    (async/close! input)
    (assoc component :input nil :bus nil))

  IEventBus
  (subscribe [_ channel]
    (async/tap bus channel))

  (send-message [_ message]
    (async/go
      (async/>! input message))))

(alter-meta! #'->EventBus assoc :private true)
(alter-meta! #'map->EventBus assoc :private true)

(defn eventbus
  "The EventBus constructor."
  []
  (map->EventBus {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fake Messager
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Messager [stoped eventbus]
  component/Lifecycle
  (start [component]
    (vreset! stoped false)
    (async/go-loop []
      (when-not @stoped
        (async/<! (send-message eventbus (str (java.time.Instant/now))))
        (async/<! (async/timeout 1000))
        (recur)))
    component)

  (stop [component]
    (vreset! stoped true)))

(defn messager
  []
  (->Messager (volatile! nil) nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Web Application
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn index-page
  [context]
  (let [content (slurp (io/resource "index.html"))]
    (http/ok content {:content-type "text/html"})))

(defn events-page
  "A server-sent envents endpoint for send
  the messages to the client."
  [context out]
  (let [bus (get-in context [::app :eventbus])]
    (subscribe bus out)))

(defrecord WebApp [eventbus server]
  component/Lifecycle
  (start [this]
    (let [routes [[:all (ctcomp/extra-data {::app this})]
                  [:get index-page]
                  [:get "events" (with-meta events-page {:type :sse})]]]
      (ctcomp/assoc-routes! server ::web routes)))

  (stop [this]
    ;; noop
    ))

(alter-meta! #'->WebApp assoc :private true)
(alter-meta! #'map->WebApp assoc :private true)

(defn webapp
  []
  (->WebApp nil nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn application-system
  "The application system constructor."
  []
  (-> (component/system-map
       :catacumba (ctcomp/catacumba-server {:port 5050})
       :eventbus (eventbus)
       :messager (messager)
       :app (webapp))
      (component/system-using
       {:app {:server :catacumba
              :eventbus :eventbus}
        :messager {:eventbus :eventbus}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  "The main entry point to your application."
  [& args]
  (component/start (application-system)))
