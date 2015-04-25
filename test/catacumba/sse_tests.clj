(ns catacumba.sse-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close!
                                        go-loop onto-chan timeout <!!]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            ;; [clj-http.client :as client]
            [qbits.jet.client.websocket :as ws]
            [qbits.jet.client.http :as http]
            [catacumba.core :as ct]
            [catacumba.core-tests :refer [with-server]]))

(def client (http/client))

(deftest sse-standard-handler
  (letfn [(sse-handler [context out]
            (go
              (>! out "1")
              (>! out {:data "2"})
              (>! out {:event "foobar"})
              (>! out {:id "foobar"})
              (>! out {:id "foobar" :data "3"})
              (close! out)))
          (handler [context]
            (ct/sse context sse-handler))]
    (with-server (with-meta handler {:type :ratpack})
      (let [p (promise)
            response (<!! (http/get client "http://localhost:5050/" {:fold-chunked-response? false}))]
        (is (= (:status response) 200))
        (is (= (-> response :body <!!) "data: 1\n\n"))
        (is (= (-> response :body <!!) "data: 2\n\n"))
        (is (= (-> response :body <!!) "event: foobar\n\n"))
        (is (= (-> response :body <!!) "id: foobar\n\n"))
        (is (= (-> response :body <!!) "data: 3\nid: foobar\n\n"))))))

(deftest sse-specific-handler
  (letfn [(sse-handler [context out]
            (go
              (>! out "1")
              (>! out {:data "2"})
              (>! out {:event "foobar"})
              (>! out {:id "foobar"})
              (>! out {:id "foobar" :data "3"})
              (close! out)))]
    (with-server (with-meta sse-handler {:type :sse})
      (let [p (promise)
            response (<!! (http/get client "http://localhost:5050/" {:fold-chunked-response? false}))]
        (is (= (:status response) 200))
        (is (= (-> response :body <!!) "data: 1\n\n"))
        (is (= (-> response :body <!!) "data: 2\n\n"))
        (is (= (-> response :body <!!) "event: foobar\n\n"))
        (is (= (-> response :body <!!) "id: foobar\n\n"))
        (is (= (-> response :body <!!) "data: 3\nid: foobar\n\n"))))))
