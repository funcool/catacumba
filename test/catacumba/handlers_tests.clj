(ns catacumba.handlers-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close!
                                        go-loop onto-chan timeout <!!]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [catacumba.core :as ct]
            [catacumba.handlers :as handlers]
            [catacumba.handlers.session :as session]
            [catacumba.core-tests :refer [with-server base-url]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CORS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cors-config1 {:origin "*"
                  :allow-headers ["X-FooBar"]
                  :max-age 3600})

(def cors-config2 {:origin #{"http://localhost/"}})

(deftest cors-handler
  (testing "Simple cors request"
    (let [handler (fn [ctx] "hello world")
          handler (ct/routes [[:any (handlers/cors cors-config1)]
                              [:get handler]])]
      (with-server handler
        (let [response (client/get base-url {:headers {"Origin" "http://localhost/"}})
              headers (:headers response)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (is (= (get headers "access-control-allow-origin") "*"))
          (is (= (get headers "access-control-allow-headers") "X-FooBar"))))))

  (testing "Options cors request"
    (let [handler (fn [ctx] "hello world")
          handler (ct/routes [[:any (handlers/cors cors-config1)]
                              [:get handler]])]
      (with-server handler
        (let [response (client/options base-url {:headers {"Origin" "http://localhost/"
                                                           "access-control-request-method" "post"}})
              headers (:headers response)]
          (is (= (:body response)) "")
          (is (= (:status response) 200))
          (is (= (get headers "access-control-allow-origin") "*"))
          (is (= (get headers "access-control-max-age") "3600"))))))

  (testing "Wrong cors request"
    (let [handler (fn [ctx] "hello world")
          handler (ct/routes [[:any (handlers/cors cors-config2)]
                              [:get handler]])]
      (with-server handler
        (let [response (client/options base-url {:headers {"Origin" "http://localhast/"
                                                           "access-control-request-method" "post"}})
              headers (:headers response)]
          (is (= (:body response)) "")
          (is (= (:status response) 200))
          (is (= (get headers "access-control-allow-origin") nil))
          (is (= (get headers "access-control-max-age") nil))))))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic request in context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest basic-request-handler
  (testing "Simple cors request"
    (let [p (promise)
          handler (fn [ctx] (deliver p ctx) "hello world")
          handler (ct/routes [[:any handlers/basic-request]
                              [:any handler]])]
      (with-server handler
        (let [response (client/get (str base-url "/foo"))
              ctx (deref p 1000 {})]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (is (= (:method ctx) :get))
          (is (= (:path ctx) "/foo")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Session tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest session-handler-tests
  (testing "Simple session access."
    (let [p (promise)
          handler (fn [ctx]
                    (let [session (:session ctx)]
                      (swap! session assoc :foo 2)
                      (if (= (count @session) 0)
                        (swap! session assoc :foo 2)
                        (deliver p @session))
                      "hello"))
          handler (ct/routes [[:any (session/session-handler {})]
                              [:any handler]])]
      (with-server handler
        (let [response (client/get (str base-url "/foo"))
              cookie (get-in response [:cookies "sessionid"])]
          (is (map? cookie))
          (is (:value cookie))
          (is (= (:status response) 200))
          (let [cookie {:value (:value cookie)}
                response' (client/get (str base-url "/foo") {:cookies {"sessionid" cookie}})]
            (is (= (:status response') 200))
            (is (= (deref p 1000 nil) {:foo 2})))))))

  (testing "Session type behavior"
    (let [s (session/session "foobar")]
      (is (not (#'session/accessed? s)))
      (is (not (#'session/modified? s)))
      (is (#'session/empty? s)))

    (let [s (session/session "foobar")]
      (deref s)
      (is (#'session/accessed? s))
      (is (not (#'session/modified? s)))
      (is (#'session/empty? s)))

    (let [s (session/session "foobar")]
      (swap! s assoc :foo 2)
      (is (#'session/accessed? s))
      (is (#'session/modified? s))
      (is (not (#'session/empty? s)))))

  (testing "In memory session storage"
    (let [st (session/memory-storage)]
      (is (nil? (#'session/load-data st :foo)))
      (#'session/persist-data st :foo {:bar 2})
      (is (= (#'session/load-data st :foo) {:bar 2}))))
)
