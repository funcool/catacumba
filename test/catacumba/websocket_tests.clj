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


(deftest websocket-handshake-standard-handler
  (letfn [(websocket [{:keys [in out]}]
            (go
              (let [received (<! in)]
                (>! out "PONG")
                (close! out))))
          (handler [context]
            (ct/websocket context websocket))]
    (with-server handler
      (let [p (promise)]
        (ws/connect! "ws://localhost:5050/"
                     (fn [{:keys [in out]}]
                       (go
                         (>! out "PING")
                         (when (= "PONG" (<! in))
                           (close! out)
                           (deliver p true)))))
        (is (deref p 1000 false))))))

(deftest websocket-handshake-websocket-handler
  (letfn [(handler [{:keys [in out]}]
            (go
              (let [received (<! in)]
                (>! out "PONG")
                (close! out))))]
    (with-server (with-meta handler
                   {:handler-type :catacumba/websocket})
      (let [p (promise)]
        (ws/connect! "ws://localhost:5050/"
                     (fn [{:keys [in out]}]
                       (go
                         (>! out "PING")
                         (when (= "PONG" (<! in))
                           (close! out)
                           (deliver p true)))))
        (is (deref p 1000 false)))))
)

(deftest websockets-backpressure
  (let [p1 (promise)
        p2 (promise)
        p3 (promise)]
    (letfn [(handler [{:keys [in out]}]
              (go
                (let [received (<! in)]
                  (deliver p1 received))
                (let [received (<! in)]
                  (deliver p2 received))
                (let [received (<! in)]
                  (deliver p3 received))
                (>! out "PONG")
                (close! out)))]
      (with-server (with-meta handler
                     {:handler-type :catacumba/websocket})
        (let [p4 (promise)]
          (ws/connect! "ws://localhost:5050/"
                       (fn [{:keys [in out]}]
                         (go
                           (>! out "foo")
                           (<! (timeout 100))
                           (>! out "bar")
                           (<! (timeout 100))
                           (>! out "baz")
                           (when (= "PONG" (<! in))
                             (close! out)
                             (deliver p4 true)))))
          (is (deref p4 2000 false)))))))
