(ns catacumba.stomp-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close! go-loop onto-chan timeout]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [catacumba.core :as ct]
            [catacumba.experimental.stomp :as stomp]))


(deftest message-broker-tests
  (testing "Simple subscription."
    (let [p (promise)]
      (with-open [broker (stomp/message-broker {})]
        (stomp/subscribe! broker "topic" :foo (fn [m] (deliver p m)))
        (stomp/send! broker "topic" 123)
        (is (= (deref p 2000 nil) 123))))))
