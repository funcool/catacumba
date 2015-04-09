(ns catacumba.core-tests
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [catacumba.core :as ct]
            [catacumba.http :as http])
  (:import ratpack.registry.Registries))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-server [handler & body]
  `(let [server# (ct/run-server ~handler)]
     (try
       ~@body
       (finally (.stop server#)))))

(def base-url "http://localhost:5050")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest request-response
  (testing "Using send! with context"
    (let [handler (fn [ctx] (ct/send! ctx "hello world"))]
      (with-server (with-meta handler {:type :ratpack})
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using string as return value."
    (let [handler (fn [ctx] "hello world")]
      (with-server (with-meta handler {:type :ratpack})
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using client ns functions."
    (let [handler (fn [ctx]
                    (http/ok "hello world" {:x-header "foobar"}))]
      (with-server (with-meta handler {:type :ratpack})
        (let [response (client/get base-url)]
          (is (= (get-in response [:headers "x-header"]) "foobar"))
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))
)

(deftest routing
  (testing "Routing with parameter."
    (let [handler (fn [ctx]
                    (let [params (ct/route-params ctx)]
                      (str "hello " (:name params))))
          handler (ct/routes [[:get ":name" handler]])]
      (with-server handler
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "hello foo"))
          (is (= (:status response) 200))))))

  (testing "Routing assets with prefix."
    (let [handler (ct/routes [[:prefix "static"
                               [:assets "public"]]])]
      (with-server handler
        (let [response (client/get (str base-url "/static/test.txt"))]
          (is (= (:body response) "hello world from test.txt\n"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers."
    (let [handler1 (fn [ctx]
                     (ct/delegate ctx {:foo "bar"}))
          handler2 (fn [ctx]
                     (let [params (ct/context-params ctx)]
                       (str "hello " (:foo params))))
          router (ct/routes [[:get "" handler1 handler2]])]
      (with-server router
        (let [response (client/get (str base-url ""))]
          (is (= (:body response) "hello bar"))
          (is (= (:status response) 200))))))
)
