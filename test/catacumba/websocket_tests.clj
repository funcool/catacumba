(ns catacumba.websocket-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close! go-loop onto-chan timeout]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [qbits.jet.client.websocket :as ws]
            [catacumba.core :as ct]
            [catacumba.core-tests :refer [with-server]]))


(deftest experiments
  (testing "Start websocket from the standard handler"
    (letfn [(websocket [{:keys [in out]}]
              (go
                (let [received (<! in)]
                  (>! out "PONG")
                  (close! out))))
            (handler [context]
              (ct/websocket context websocket))]
      (with-server (with-meta handler {:type :ratpack})
        (let [p (promise)]
          (ws/connect! "ws://localhost:5050/"
                       (fn [{:keys [in out]}]
                         (go
                           (>! out "PING")
                           (when (= "PONG" (<! in))
                             (close! out)
                             (deliver p true)))))
          (is (deref p 1000 false))))))

  (testing "Start websocket from the standard handler"
    (letfn [(handler [{:keys [in out]}]
              (go
                (let [received (<! in)]
                  (>! out "PONG")
                  (close! out))))]
      (with-server (with-meta handler {:type :websocket})
        (let [p (promise)]
          (ws/connect! "ws://localhost:5050/"
                       (fn [{:keys [in out]}]
                         (go
                           (>! out "PING")
                           (when (= "PONG" (<! in))
                             (close! out)
                             (deliver p true)))))
          (is (deref p 1000 false))))))
)


