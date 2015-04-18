(ns catacumba.experimental.stomp.broker
  "A simple, in memmory pub sub broker implementation."
  (:require [clojure.core.async :as async])
  (:import ratpack.handling.Handler
           ratpack.handling.Context))


;; (defmulti dispatch-frame (comp :command first))

;; (defmethod dispatch-frame :subscribe
;;   [frame broker {:keys [out] :as opts}]
;;   (let [subscriber (fn [message]
;;                      (async/go
;;                        (let [strmsg (str message)]
;;                          (async/>! out strmsg))))
;;         topic (get-in frame [:headers "destination"])
;;         key (generate-key topic opts)]
;;     (broker/subscribe! broker key subscriber)))


;; (defn- process-frame
;;   [frame broker {:keys [out] :as opts}]
;;   (async/go
;;     (try
;;       (let [frame (async/<! (-> (parser/parse frame)
;;                                 (dispatch-frame broker opts)))]
;;         (async/>! out (str frame)))
;;       (catch Exception e
;;         (let [errorframe (parser/error (.getMessage e))]
;;           (async/>! out errorframe))))))

;; (defn- websocket-handler
;;   [broker {:key [in out ctrl] :as opts}]
;;   (async/go-loop []
;;     (when-let [frame (async/<! in)]
;;       (async/<! (process-frame frame broker opts)))))

;; (defn handler
;;   []
;;   (let [broker (broker/broker)]
;;     (fn [^Context context]
;;       (ct/websocket context (partial websocket-handler broker)))))
