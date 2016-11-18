(ns catacumba.tests.test-websockets
  (:require [clojure.core.async :as a]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [catacumba.core :as ct]
            [catacumba.testing :refer [with-server]]))


(deftest websocket-handshake-standard-handler
  (letfn [(websocket [{:keys [in out]}]
            (a/go
              (let [received (a/<! in)]
                (a/>! out "PONG")
                (a/close! out))))
          (handler [context]
            (ct/websocket context websocket))]
    (with-server {:handler handler}
      (let [conn @(http/websocket-client "ws://localhost:5050/")]
        (deref (s/put! conn "PING"))
        (let [rsp @(s/take! conn)]
          (is (= "PONG" rsp))
          (s/close! conn))))))

(deftest websocket-handshake-websocket-handler
  (letfn [(handler [{:keys [in out]}]
            (a/go
              (let [received (a/<! in)]
                (a/>! out "PONG")
                (a/close! out))))]
    (with-server {:handler (with-meta handler
                             {:handler-type :catacumba/websocket})}

      (let [conn @(http/websocket-client "ws://localhost:5050/")]
        (deref (s/put! conn "PING"))
        (let [rsp @(s/take! conn)]
          (is (= "PONG" rsp))
          (s/close! conn))))))

(deftest websockets-backpressure
  (let [p1 (promise)
        p2 (promise)
        p3 (promise)
        p4 (promise)]
    (letfn [(handler [{:keys [in out]}]
              (a/go
                (let [received (a/<! in)]
                  (deliver p1 received))
                (let [received (a/<! in)]
                  (deliver p2 received))
                (let [received (a/<! in)]
                  (deliver p3 received))
                (a/>! out "PONG")
                (a/close! out)))]

      (with-server {:handler (with-meta handler {:handler-type :catacumba/websocket})}
        (a/thread
          (let [conn @(http/websocket-client "ws://localhost:5050/")]
            @(s/put! conn "foo")
            (a/<!! (a/timeout 100))
            @(s/put! conn "bar")
            (a/<!! (a/timeout 100))
            @(s/put! conn "baz")
            (let [rsp @(s/take! conn)]
              (when (= rsp "PONG")
                (deliver p4 true)))))
        (is (deref p4 2000 false))))))
