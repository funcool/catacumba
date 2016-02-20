(ns catacumba.handlers-postal-tests
  (:refer-clojure :exclude [future])
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as a]
            [aleph.http :as http]
            [clj-http.client :as client]
            [byte-streams :as bs]
            [manifold.deferred :as md]
            [manifold.stream :as ms]
            [promesa.core :as p]
            [buddy.core.codecs :as codecs]
            [cats.core :as m]
            [cats.monad.exception :as exc]
            [catacumba.core :as ct]
            [catacumba.impl.executor :as exec]
            [catacumba.handlers.postal :as pc]
            [catacumba.testing :refer [with-server]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro future
  "Takes a body of expressions and yields a promise object that will
  invoke the body in another thread.
  This is a drop in replacement for the clojure's builtin `future`
  function that return composable promises."
  [& body]
  `(let [fun# (fn [] ~@body)]
     (exec/submit fun#)))

(defn- response->data
  [response]
  (let [inputstream (:body response)]
    (bs/convert inputstream (Class/forName "[B"))))

(defn- response->frame
  [response]
  (let [data (response->data response)]
    (pc/-decode data :application/transit+json)))

(defn- send-raw-frame
  [uri method frame content-type]
  (let [headers {"content-type" content-type}]
    (condp = method
      :put (http/put uri {:body frame :headers headers})
      :get (http/get (str uri "?d=" (codecs/bytes->safebase64 frame))
                     {:headers headers}))))

(defn- send-raw-frame2
  [uri method frame content-type]
  (let [headers {"content-type" content-type}]
    (condp = method
      :put (client/put uri {:body frame :headers headers})
      :get (client/get (str uri "?d=" (codecs/bytes->safebase64 frame))
                       {:headers headers}))))

(defn- send-frame
  ([uri frame]
   (send-frame uri frame :put))
  ([uri frame method]
   (let [data (pc/-encode frame :application/transit+json)]
     @(md/chain
       (send-raw-frame uri method data "application/transit+json")
       response->frame))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def base-url "http://localhost:5050/")

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
                    @(send-raw-frame base-url :put frame "application/edn"))]
      (is (exc/failure? response))
      (let [response (m/extract response)
            data (ex-data response)]
        (is (:status data) 415)))))

(deftest response-as-promise-spec
  (letfn [(handler [context frame]
            (m/mlet [_ (future
                         (Thread/sleep 500))]
              (m/return {:data {:foo [1]}})))]

    (with-server {:handler (pc/router handler)}
      (let [frame {:type :query :data nil}
            response (send-frame base-url frame)]
        (is (= (:type response) :response))
        (is (= (:data response) {:foo [1]}))))))

(deftest response-as-rejected-promise-spec
  (letfn [(handler [context frame]
            (m/mlet [_ (future
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

(deftest request-using-get-method-spec
  (letfn [(handler [context frame]
            {:data frame})]
    (with-server {:handler (pc/router handler)}
      (let [frame {:type :query :data nil}
            response (send-frame base-url frame :get)]
        (is (= (:type response) :response))
        (is (= (:data response) frame))))))

(deftest stream-like-handler-spec
  (letfn [(handler [context frame]
            (pc/socket context socket-handler))
          (socket-handler [{:keys [out]}]
            (a/go
              (a/>! out {:data [1]})
              (a/close! out)))]
    (with-server {:handler (pc/router handler)}
      (let [frame {:type :subscribe :data nil}
            frame (pc/-encode frame :application/transit+json)
            conn @(http/websocket-client (str "ws://localhost:5050/?d="
                                              (codecs/bytes->safebase64 frame)))
            result @(ms/take! conn)
            frame (pc/-decode (codecs/str->bytes result)
                             :application/transit+json)]
        (is (= frame {:data [1], :type :response}))))))
