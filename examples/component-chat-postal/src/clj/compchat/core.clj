(ns compchat.core
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as a]
            ;; [clojure.java.io :as io]
            [catacumba.core :as ct]
            ;; [catacumba.http :as http]
            [catacumba.components :as ctcomp]
            [catacumba.handlers.postal :as pc])
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
    (let [chan (a/chan)]
      (assoc component :input chan :bus (a/mult chan))))

  (stop [component]
    (a/close! input)
    (assoc component :input nil :bus nil))

  IEventBus
  (subscribe [_ channel]
    (a/tap bus channel))

  (send-message [_ message]
    (a/go
      (a/>! input message))))

(alter-meta! #'->EventBus assoc :private true)
(alter-meta! #'map->EventBus assoc :private true)

(defn eventbus
  "The EventBus constructor."
  []
  (map->EventBus {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Web Application
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti api
  (comp (juxt :type :dest) second vector))

(defmethod api [:novelty :chats]
  [context {:keys [data]}]
  (let [bus (get-in context [::app :eventbus])]
    (when (not (empty? data))
      (send-message bus data))
    (pc/frame {:status :received})))

(defmethod api [:subscribe :chats]
  [context frame]
  (let [bus (get-in context [::app :eventbus])]
    (letfn [(on-socket [{:keys [in out ctrl]}]
              (let [xf (map #(pc/frame :message %))
                    ch (a/chan 1 xf)]
                (subscribe bus ch)

                (a/go-loop []
                  (let [[msg port] (a/alts! [ctrl ch])]
                    (cond
                      (= port ctrl)
                      (a/close! ch)

                      (= port ch)
                      (do
                        (a/>! out msg)
                        (recur)))))))]
      (pc/socket context on-socket))))

;; Web Component

(defrecord WebApp [eventbus server]
  component/Lifecycle
  (start [this]
    (let [routes [[:any (ctcomp/extra-data {::app this})]
                  [:any "api" (pc/router api)]
                  [:assets "" {:dir "public"
                               :indexes ["index.html"]}]]]
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
       :app (webapp))
      (component/system-using
       {:app {:server :catacumba
              :eventbus :eventbus}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  "The main entry point to your application."
  [& args]
  (component/start (application-system)))
