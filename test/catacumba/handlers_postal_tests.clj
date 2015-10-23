(ns catacumba.handlers-postal-tests
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.deferred :as md]
            [promissum.core :as p]
            [cats.core :as m]
            [cats.monad.exception :as exc]
            [catacumba.core :as ct]
            [catacumba.handlers.postal :as pc]
            [catacumba.testing :refer [with-server]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- response->data
  [response]
  (let [inputstream (:body response)]
    (bs/convert inputstream (Class/forName "[B"))))

(defn- response->frame
  [response]
  (let [data (response->data response)]
    (pc/decode data :application/transit+json)))

(defn- send-raw-frame
  [uri frame content-type]
  (let [headers {"content-type" content-type}]
    (http/put uri {:body frame :headers headers})))

(defn- send-frame
  [uri frame]
  (let [data (pc/encode frame :application/transit+json)]
    @(md/chain
      (send-raw-frame uri data "application/transit+json")
      response->frame)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def base-url "http://localhost:5050")

(deftest basic-communication-spec
  (let [p (promise)
        h (fn [context frame]
            (deliver p frame)
            {:data [1 2 3]})]
    (with-server {:handler (pc/router h)}
      (let [frame {:type :query :data [3 3 3]}
            response (send-frame base-url frame)]
        (is (= (:type response) :response))
        (is (= (:data response) [1 2 3]))
        (let [request (deref p 1000 nil)]
          (is (= request frame)))))))

(deftest invalid-response-spec
  (with-server {:handler (pc/router (constantly {}))}
    (let [frame (pr-str {:type :query :data nil})
          response (exc/try-on
                    @(send-raw-frame base-url frame "application/edn"))]
      (is (exc/failure? response))
      (let [response (m/extract response)
            data (ex-data response)]
        (is (:status data) 415)))))

(deftest response-as-promise-spec
  (letfn [(handler [context frame]
            (m/mlet [_ (p/future
                         (Thread/sleep 500))]
              (m/return {:data {:foo [1]}})))]

    (with-server {:handler (pc/router handler)}
      (let [frame {:type :query :data nil}
            response (send-frame base-url frame)]
        (is (= (:type response) :response))
        (is (= (:data response) {:foo [1]}))))))

(deftest response-as-rejected-promise-spec
  (letfn [(handler [context frame]
            (m/mlet [_ (p/future
                         (Thread/sleep 500))]
              (throw (ex-info "foobar" {:error true}))
              (m/return {:data {:foo [1]}})))]

    (with-server {:handler (pc/router handler)}
      (let [frame {:type :query :data nil}
            response (send-frame base-url frame)]
        (is (= (:type response) :error))
        (is (= (:data response) {:error true}))))))


(deftest response-as-deferred-spec
  (letfn [(handler [context frame]
            (md/future
              (Thread/sleep 100)
              {:data {:foo [1]}}))]
    (with-server {:handler (pc/router handler)}
      (let [frame {:type :query :data nil}
            response (send-frame base-url frame)]
        (is (= (:type response) :response))
        (is (= (:data response) {:foo [1]}))))))

