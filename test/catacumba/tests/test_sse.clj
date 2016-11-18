(ns catacumba.tests.test-sse
  (:require [clojure.core.async :refer [put! take! chan <! >! go close!
                                        go-loop onto-chan timeout <!!]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [catacumba.core :as ct]
            [catacumba.testing :refer [with-server]]))

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
    (with-server {:handler handler}
      (let [p (promise)
            response (client/get "http://localhost:5050/")]
        (is (= (:status response) 200))
        (is (= (:body response)
               (str "data: 1\n\n"
                    "data: 2\n\n"
                    "event: foobar\n\n"
                    "id: foobar\n\n"
                    "id: foobar\n"
                    "data: 3\n\n")))))))

(deftest sse-specific-handler
  (letfn [(sse-handler [context out]
            (go
              (>! out "1")
              (>! out {:data "2"})
              (>! out {:event "foobar"})
              (>! out {:id "foobar"})
              (>! out {:id "foobar" :data "3"})
              (close! out)))]
    (with-server {:handler (with-meta sse-handler
                             {:handler-type :catacumba/sse})}
      (let [p (promise)
            response (client/get "http://localhost:5050/" {:fold-chunked-response? false})]
        (is (= (:status response) 200))
        (is (= (:body response)
               (str "data: 1\n\n"
                    "data: 2\n\n"
                    "event: foobar\n\n"
                    "id: foobar\n\n"
                    "id: foobar\n"
                    "data: 3\n\n")))))))
