(ns catacumba.handlers-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close!
                                        go-loop onto-chan timeout <!!]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.sign.jws :as jws]
            [slingshot.slingshot :refer [try+]]
            [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.handlers :as hs]
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
          handler (ct/routes [[:any (hs/cors cors-config1)]
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
          handler (ct/routes [[:any (hs/cors cors-config1)]
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
          handler (ct/routes [[:any (hs/cors cors-config2)]
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
          handler (ct/routes [[:any hs/basic-request]
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
          handler (ct/routes [[:any (hs/session {})]
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
    (let [s (#'session/->session "foobar")]
      (is (not (#'session/accessed? s)))
      (is (not (#'session/modified? s)))
      (is (#'session/empty? s)))

    (let [s (#'session/->session "foobar")]
      (deref s)
      (is (#'session/accessed? s))
      (is (not (#'session/modified? s)))
      (is (#'session/empty? s)))

    (let [s (#'session/->session "foobar")]
      (swap! s assoc :foo 2)
      (is (#'session/accessed? s))
      (is (#'session/modified? s))
      (is (not (#'session/empty? s)))))

  (testing "In memory session storage"
    (let [st (session/memory-storage)]
      (is (nil? (#'session/read-session st :foo)))
      (#'session/write-session st :foo {:bar 2})
      (is (= (#'session/read-session st :foo) {:bar 2}))))
)

(deftest interceptors-tests
  (let [counter (atom 0)
        p1 (promise)
        p2 (promise)
        p3 (promise)
        p4 (promise)]
    (letfn [(interceptor [_ type continuation]
              (deliver p1 (swap! counter inc))
              (continuation)
              (deliver p2 (swap! counter inc)))

            (handler2 [context]
              (deliver p3 (swap! counter inc))
              (ct/delegate context))

            (handler3 [context]
              (deliver p4 (swap! counter inc))
              "hello world")]
      (with-server (ct/routes [[:interceptor interceptor]
                               [:any handler2]
                               [:any handler3]])
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (is (= (deref p1 1000 nil) 1))
          (is (= (deref p2 1000 nil) 4))
          (is (= (deref p3 1000 nil) 2))
          (is (= (deref p4 1000 nil) 3)))))))


(def auth-backend
  (jws-backend {:secret "secret"}))

(deftest auth-tests
  (letfn [(handler [context]
            (if (:identity context)
              (http/ok "Identified")
              (http/unauthorized "Unauthorized")))]
    (with-server (ct/routes [[:auth auth-backend]
                             [:any handler]])
      (try+
       (let [response (client/get base-url)]
         (is (= (:status response) 401)))
       (catch Object e
         (is (= (:status e) 401))))

      (let [token (jws/sign {:userid 1} "secret")
            headers {"Authorization" (str "Token " token)}
            response (client/get base-url {:headers headers})]
        (is (= (:status response) 200))))))

