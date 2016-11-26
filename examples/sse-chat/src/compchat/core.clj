(ns compchat.core
  (:require [clojure.core.async :as a]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [catacumba.core :as ct]
            [catacumba.handlers.parse :as parse]
            [catacumba.http :as http])
  (:gen-class))

;; --- Event Bus

(defonce input (a/chan))
(defonce bus (a/mult input))

(defn subscribe!
  [chan]
  (a/tap bus chan))

(defn send!
  [msg]
  (a/put! input msg))

(def actor (agent nil))

(defn log
  [& args]
  (send actor (fn [v]
                (apply println args))))

(a/go-loop [i 1]
  (let [data {:author "bot" :message (str "hello " i)}
        msg (json/encode data)]
    ;; (log "daemon: sending message")
    (a/>! input msg)
    ;; (log "daemon: message sent")
    (a/<! (a/timeout 1000))
    (recur (inc i))))


;; --- Web App

(defn index-page
  [context]
  (let [content (slurp (io/resource "index.html"))]
    (http/ok content {:content-type "text/html"})))

(defn post-message
  "A simple handler for receive messages via post."
  [{:keys [data] :as context}]
  (if data
    (let [msg (json/encode data)]
      (send! msg)
      (http/ok "Everything ok"))
    (http/bad-request "Wrong parameters")))

(defn events-page
  "A server-sent envents endpoint for send
  the messages to the client."
  {:handler-type :catacumba/sse}
  [{:keys [out ctrl] :as context}]
  (let [sub (a/tap bus (a/chan))
        id (str (gensym "ev"))]
    (a/go-loop []
      ;; (log ">>>" id "go-loop:start")
      (let [[msg port] (a/alts! [sub ctrl])]
        ;; (log ">>>" id "go-loop:received close? =" (= port close))
        (if (= port sub)
          (let [[msg port] (a/alts! [(a/timeout 200) [out msg]])]
            ;; (log ">>>" id "go-loop:sent timeout? =" (not= port output))
            (if (= port out)
              (when msg (recur))
              (do
                (log ">>>" id "go-loop:close because timeout")
                (a/close! sub)
                (a/close! out))))
          (do
            (log ">>>" id "go-loop:close because client")
            (a/close! sub)
            (a/close! out)))))))

(defn routes
  []
  (ct/routes
   [[:get "" #'index-page]
    [:get "events" #'events-page]
    [:all (parse/body-params)]
    [:post "events" #'post-message]]))

;; --- Entry Point

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server (routes) {:port 5050}))
