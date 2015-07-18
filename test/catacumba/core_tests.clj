(ns catacumba.core-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close! go-loop onto-chan timeout] :as a]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [promissum.core :as p]
            [catacumba.stream :as stream]
            [cats.core :as m]
            [cuerdas.core :as str]
            [manifold.stream :as ms]
            [manifold.deferred :as md]
            [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.impl.parse :as parse]
            [catacumba.testing :refer [with-server]]
            [catacumba.handlers.interceptor])
  (:import ratpack.exec.Execution
           ratpack.func.Action
           ratpack.func.Block
           ratpack.exec.ExecInterceptor
           ratpack.exec.ExecInterceptor$ExecType))

(def base-url "http://localhost:5050")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest public-address
  (let [p (promise)
        handler (fn [context]
                  (deliver p (ct/public-address context))
                  "hello world")]
    (with-server {:handler handler}
      (let [response (client/get base-url)
            uri (deref p 1000 nil)]
        (is (= (str uri) "http://localhost:5050")))))
)

(deftest cookies
  (testing "Setting new cookie."
    (letfn [(handler [context]
              (ct/set-cookies! context {:foo {:value "bar" :secure true :http-only true}})
              "hello world")]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (contains? response :cookies))
          (is (= (get-in response [:cookies "foo" :path]) "/"))
          (is (= (get-in response [:cookies "foo" :value]) "bar"))
          (is (= (get-in response [:cookies "foo" :secure]) true)))))))

(deftest request-response
  (testing "Using send! with context"
    (let [handler (fn [ctx] (ct/send! ctx "hello world"))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using string as return value."
    (let [handler (fn [ctx] "hello world")]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using client ns functions."
    (let [handler (fn [ctx]
                    (http/ok "hello world" {:x-header "foobar"}))]
      (with-server {:handler handler}
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
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using channel as response."
    (letfn [(handler [ctx]
              (go
                (<! (timeout 100))
                "hello world"))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using promise as response."
    (letfn [(handler [ctx]
              (m/mlet [x (p/promise (fn [resolve]
                                      (async/<!! (async/timeout 1000))
                                      (resolve "hello")))]
                (m/return (str x " world"))))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using publisher as body"
    (letfn [(handler [ctx]
              (let [p (stream/publisher ["hello" " " "world"])
                    p (stream/transform (map str/upper) p)]
                (http/accepted p)))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "HELLO WORLD"))
          (is (= (:status response) 202))))))

  (testing "Using manifold deferred as body."
    (letfn [(handler [ctx]
              (let [d (md/deferred)]
                (async/thread
                  (md/success! d "hello world"))
                (http/accepted d)))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 202))))))

  (testing "Using manifold stream as body."
    (letfn [(handler [ctx]
              (let [d (ms/stream 3)]
                (async/thread
                  @(ms/put! d "hello")
                  @(ms/put! d " ")
                  @(ms/put! d "world")
                  (ms/close! d))
                (http/accepted d)))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 202))))))
)

(deftest routing
  (testing "Routing with parameter."
    (let [handler (fn [ctx]
                    (let [params (:route-params ctx)]
                      (str "hello " (:name params))))
          handler (ct/routes [[:get ":name" handler]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "hello foo"))
          (is (= (:status response) 200))))))

  (testing "Routing assets with prefix."
    (let [handler (ct/routes [[:assets "static" {:dir "resources/public"}]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/static/test.txt"))]
          (is (= (:body response) "hello world from test.txt\n"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers in one route."
    (let [handler1 (fn [ctx]
                     (ct/delegate ctx {:foo "bar"}))
          handler2 (fn [ctx]
                     (str "hello " (:foo ctx)))
          router (ct/routes [[:get "" handler1 handler2]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url ""))]
          (is (= (:body response) "hello bar"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers in more than one route."
    (let [handler1 (fn [ctx]
                     (ct/delegate ctx {:foo "bar"}))
          handler2 (fn [ctx]
                     (str "hello " (:foo ctx)))
          router (ct/routes [[:prefix "foo"
                              [:any handler1]
                              [:get handler2]]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "hello bar"))
          (is (= (:status response) 200))))))

  (testing "User defined error handler"
    (let [error-handler (fn [ctx error] (http/ok "no error"))
          handler (fn [ctx] (throw (Exception. "foobar")))
          router (ct/routes [[:error error-handler]
                             [:any handler]])]
      (with-server {:handler router}
        (let [response (client/get base-url)]
          (is (= (:body response) "no error"))
          (is (= (:status response) 200))))))

  (testing "User defined error handler"
    (let [error-handler1 (fn [ctx error] (http/ok "no error1"))
          error-handler2 (fn [ctx error] (http/ok "no error2"))
          handler (fn [ctx] (throw (Exception. "foobar")))
          router (ct/routes [[:prefix "foo"
                              [:error error-handler1]
                              [:any handler]]
                             [:prefix "bar"
                              [:error error-handler2]
                              [:any handler]]])]
      (with-server {:handler router}
        (let [response1 (client/get (str base-url "/foo"))
              response2 (client/get (str base-url "/bar"))]
          (is (= (:body response1) "no error1"))
          (is (= (:body response2) "no error2"))
          (is (= (:status response1) 200))
          (is (= (:status response2) 200))))))

  (testing "Chaining handlers by request method"
    (let [handler1 (fn [ctx] "from get")
          handler2 (fn [ctx] "from post")
          router (ct/routes [[:by-method "foo"
                              [:get handler1]
                              [:post handler2]]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "from get"))
          (is (= (:status response) 200)))
        (let [response (client/post (str base-url "/foo"))]
          (is (= (:body response) "from post"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers with :all"
    (let [handler (fn [ctx] "from get")
          router (ct/routes [[:all "foo" handler]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "from get"))
          (is (= (:status response) 200))))))

)


(deftest request-body-handling
  (testing "Read body as text"
    (let [p (promise)
          handler (fn [ctx]
                    (let [body (ct/get-body ctx)]
                      (deliver p (slurp body)))
                    "hello world")]
      (with-server {:handler handler}
        (let [response (client/get base-url {:body "Hello world"
                                             :content-type "application/zip"})]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (let [bodydata (deref p 1000 nil)]
            (is (= bodydata "Hello world"))))))))

(deftest cps-handler-type
  (let [handler (fn [context next]
                   (a/thread
                     (a/<!! (a/timeout 500))
                     (next "hello world cps")))]
    (with-server {:handler (with-meta handler {:handler-type :catacumba/cps})}
      (let [response (client/get base-url)]
        (is (= (:body response) "hello world cps"))
        (is (= (:status response) 200))))))

;; (deftest experiments
;;   (letfn [(handler1 [context]
;;             (println 1111)
;;             "hello world")
;;           (handler4 [context]
;;             (println 4444)
;;             (ct/delegate context))
;;           (handler2 [context]
;;             (println 2222)
;;             "hello world")
;;           (handler3 [context]
;;             (println 3333)
;;             "hello world")]
;;     (with-server (ct/routes [[:insert
;;                               [:any handler4]
;;                               [:get "foo" handler1]]
;;                              [:insert
;;                               [:any handler4]
;;                               [:get "bar" handler2]]
;;                              [:any handler3]])
;;       (let [response (client/get (str base-url "/bar"))]
;;         (is (= (:body response) "hello world"))
;;         (is (= (:status response) 200))))))
