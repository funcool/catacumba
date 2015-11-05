(ns compchat.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.style :as style]
            [postal.client :as pc]
            [beicon.core :as s]
            [promesa.core :as p])
  (:import [goog.date DateTime]))

;; Instanciate the postal client
(def client (pc/client "http://localhost:5050/api"))

(defn- timestamp
  "Return a iso string formated the current time."
  []
  (let [d (DateTime.)]
    (.toIsoString d)))

(defn- build-entry
  "Build chat entry dom node."
  [author text]
  (let [author (if (empty? author)
                 "Anonymous"
                 author)
        title (str (timestamp) "(" author "): ")]
    (dom/createDom "div" #js {"class" "chat-entry"}
                   (dom/createDom "span" #js {"class" "title"} title)
                   (dom/createDom "span" #js {"class" "text"} text))))

(defn- append-message
  "Append a new received message into the chat box."
  [{:keys [data]}]
  (let [entry (build-entry (:author data)
                           (:text data))
        el (dom/getElementByClass "chatarea")]
    (dom/appendChild el entry)
    (style/scrollIntoContainerView entry el)))

(defn main
  []
  (enable-console-print!)

  (letfn [(on-key-up [event]
            (when (= 13 (.-keyCode event))
              (let [author-node (dom/getElement "author-input")
                    author (.-value author-node)
                    target (.-target event)
                    text (.-value target)
                    data {:author author
                          :text text}]
                ;; Send data to the backend
                (pc/novelty client :chats data)

                ;; Empty the text input
                (set! (.-value target) ""))))]

    (let [input (dom/getElement "message-input")
          bus (pc/subscribe client :chats)]
      (events/listen input "keyup" on-key-up)
      (s/on-value bus append-message))))

(main)
