(ns catacumba.core-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close! go-loop onto-chan timeout]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.impl.parse :as parse])
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

(deftest request-response-chunked
  (testing "Using channel as body."
    (let [handler (fn [ctx]
                    (let [ch (chan)]
                      (go
                        (<! (timeout 100))
                        (>! ch "hello ")
                        (<! (timeout 100))
                        (>! ch "world")
                        (close! ch))
                      (http/ok ch)))]
      (with-server (with-meta handler {:type :ratpack})
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using channel as response."
    (let [handler (fn [ctx]
                    (go
                      (<! (timeout 100))
                      "hello world"))]
      (with-server (with-meta handler {:type :ratpack})
        (let [response (client/get base-url)]
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

  (testing "Chaining handlers in one route."
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

  (testing "Chaining handlers in more than one route."
    (let [handler1 (fn [ctx]
                     (ct/delegate ctx {:foo "bar"}))
          handler2 (fn [ctx]
                     (let [params (ct/context-params ctx)]
                       (str "hello " (:foo params))))
          router (ct/routes [[:prefix "foo"
                              [:all handler1]
                              [:get handler2]]])]
      (with-server router
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "hello bar"))
          (is (= (:status response) 200))))))

  (testing "User defined error handler"
    (let [error-handler (fn [ctx error] (http/ok "no error"))
          handler (fn [ctx] (throw (Exception. "foobar")))
          router (ct/routes [[:error error-handler]
                             [:all handler]])]
      (with-server router
        (let [response (client/get base-url)]
          (is (= (:body response) "no error"))
          (is (= (:status response) 200))))))

  (testing "User defined error handler"
    (let [error-handler1 (fn [ctx error] (http/ok "no error1"))
          error-handler2 (fn [ctx error] (http/ok "no error2"))
          handler (fn [ctx] (throw (Exception. "foobar")))
          router (ct/routes [[:prefix "foo"
                              [:error error-handler1]
                              [:all handler]]
                             [:prefix "bar"
                              [:error error-handler2]
                              [:all handler]]])]
      (with-server router
        (let [response1 (client/get (str base-url "/foo"))
              response2 (client/get (str base-url "/bar"))]
          (is (= (:body response1) "no error1"))
          (is (= (:body response2) "no error2"))
          (is (= (:status response1) 200))
          (is (= (:status response2) 200))))))
)


(deftest form-parsing
  (testing "Multipart form parsing with multiple files."
    (let [p (promise)
          handler (fn [context]
                    (let [form (parse/parse context)]
                      (deliver p form)
                      "hello world"))]
      (with-server handler
        (let [multipart {:multipart [{:name "foo" :content "bar"}
                                     {:name "myfile"
                                      :content (-> (io/resource "public/test.txt")
                                                   (io/file))
                                      :encoding "UTF-8"
                                      :mime-type "text/plain"}
                                     {:name "myfile"
                                      :content (-> (io/resource "public/test.txt")
                                                   (io/file))
                                      :encoding "UTF-8"
                                      :mime-type "text/plain"}]}
              response (client/post base-url multipart)]
          (is (= (:status response) 200))
          (is (= (:body response) "hello world"))
          (let [formdata (deref p 1000 nil)]
            (is (= (get formdata "foo") "bar"))))))))

