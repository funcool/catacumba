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
            [catacumba.testing :refer [with-server]]
            [catacumba.handlers.interceptor])
  (:import ratpack.exec.Execution
           ratpack.func.Action
           ratpack.func.Block
           ratpack.exec.ExecInterceptor
           ratpack.exec.ExecInterceptor$ExecType
           java.io.ByteArrayInputStream))

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
        (is (= (str uri) "http://localhost:5050"))))))

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

  (testing "Using response object as return value with string as body."
    (let [handler (fn [ctx]
                    (http/ok "hello world" {:x-header "foobar"}))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (get-in response [:headers :x-header]) "foobar"))
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using channel as response."
    (let [handler (fn [ctx]
                    (go
                      (<! (timeout 100))
                      (http/ok "hello world")))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

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

  (testing "Using completable future as response."
    (letfn [(handler [ctx]
              (p/promise (fn [resolve]
                           (async/<!! (async/timeout 1000))
                           (resolve (http/ok "hello world")))))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))))))

  (testing "Using completable future as body."
    (letfn [(handler [ctx]
              (http/ok (p/promise (fn [resolve]
                                    (async/<!! (async/timeout 1000))
                                    (resolve "hello world")))))]
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

  (testing "Using manifold deferred as response."
    (letfn [(handler [ctx]
              (let [d (md/deferred)]
                (async/thread
                  (md/success! d (http/accepted "hello world")))
                d))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "hello world"))
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

  (testing "Using InputStream as body"
    (let [data (ByteArrayInputStream. (.getBytes "Hello world!" "UTF-8"))
          handler (fn [context]
                    (http/ok data {:content-type "text/plain;charset=utf-8"}))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "Hello world!"))
          (is (= (:status response) 200))))))

  (testing "Using InputStream as body"
    (let [data (.getBytes "Hello world!" "UTF-8")
          handler (fn [context]
                    (http/ok data {:content-type "text/plain;charset=utf-8"}))]
      (with-server {:handler handler}
        (let [response (client/get base-url)]
          (is (= (:body response) "Hello world!"))
          (is (= (:status response) 200))))))
)

(deftest routing
  (testing "Routing with parameter."
    (let [handler (fn [ctx]
                    (let [params (:route-params ctx)]
                      (str "hello " (:name params))))
          app (ct/routes [[:any (fn [_] (ct/delegate))]
                          [:get ":name" handler]])]
      (with-server {:handler app}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "hello foo"))
          (is (= (:status response) 200))))))

  (testing "Routing assets with prefix."
    (let [handler (ct/routes [[:assets "static" {:dir "public"}]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/static/test.txt"))]
          (is (= (:body response) "hello world from test.txt\n"))
          (is (= (:status response) 200)))))

    (let [handler (ct/routes [[:assets "" {:dir "public"}]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/test.txt"))]
          (is (= (:body response) "hello world from test.txt\n"))
          (is (= (:status response) 200)))))

    (let [handler (ct/routes [[:assets "static" {:dir "public"
                                                 :indexes ["index.html"]}]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/static/"))]
          (is (= (:body response) "hello world\n"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers in one route."
    (let [handler1 (fn [ctx]
                     (ct/delegate {:foo "bar"}))
          handler2 (fn [ctx]
                     (str "hello " (:foo ctx)))
          router (ct/routes [[:get "" handler1 handler2]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url ""))]
          (is (= (:body response) "hello bar"))
          (is (= (:status response) 200))))))

  (testing "Chaining handlers in more than one route."
    (let [handler1 (fn [ctx]
                     (ct/delegate {:foo "bar"}))
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
    (let [p (promise)
          handler1 (fn [ctx] "from get")
          handler2 (fn [ctx] "from post")
          handler3 (fn [ctx] (deliver p true) (ct/delegate))
          router (ct/routes [[:prefix "foo"
                              [:by-method
                               {:get [handler3 handler1]
                                :post handler2}]]])]
      (with-server {:handler router}
        (let [response (client/get (str base-url "/foo"))]
          (is (= (:body response) "from get"))
          (is (= (:status response) 200))
          (is (true? (deref p 1000 nil))))
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

  (testing "Routing with regex matchig"
    (letfn [(handler [ctx]
              (let [params (:route-params ctx)]
                (http/ok (:id params))))]
      (let [app (ct/routes [[:prefix ":id:\\d+"
                             [:any handler]]])]
        (with-server {:handler app}
          (let [response (client/get (str base-url "/2"))]
            (is (= (:body response) "2"))
            (is (= (:status response) 200)))
          (try
            (client/get (str base-url "/foo"))
            (throw (RuntimeException. "not expected"))
            (catch clojure.lang.ExceptionInfo e
              (let [data (ex-data e)]
                (is (= (:status data) 404)))))))))
)


(deftest request-body-handling
  (testing "Read body as text"
    (let [p (promise)
          handler (fn [ctx]
                    (let [body (:body ctx)]
                      (deliver p (slurp body)))
                    "hello world")]
      (with-server {:handler handler}
        (let [response (client/post base-url {:body "Hello world"
                                              :content-type "text/plain"})]
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

(deftest context-data-forwarding
  (letfn [(handler1 [context]
            (ct/delegate {:foo 1}))
          (handler2 [context]
            (ct/delegate {:bar 2}))
          (handler3 [context]
            (ct/delegate {:baz (+ (:foo context)
                                  (:bar context))}))
          (handler4 [p context]
            (deliver p (select-keys context [:foo :bar :baz]))
            "hello world")]
    (let [p (promise)]
      (with-server {:handler (ct/routes [[:any handler1]
                                              [:any handler2]
                                              [:any handler3]
                                              [:any (partial handler4 p)]])}
      (let [response (client/get base-url)]
        (is (= (:body response) "hello world"))
        (is (= (:status response) 200))
        (is (= (deref p 1000 nil)
               {:foo 1 :bar 2 :baz 3})))))))


(deftest async-context-delegation
  (letfn [(handler1 [context]
            (md/future
              (ct/delegate)))
          (handler2 [context]
            (md/future
              (http/accepted "hello world" {:Content-Type "plain/text"})))]
    (with-server {:handler (ct/routes [[:any handler1]
                                       [:any handler2]])}
      (let [response (client/get (str base-url))]
        (is (= (:body response) "hello world"))
        (is (= (:status response) 202))))))

;; (deftest experiments
;;   (letfn [(handler1 [context]
;;             (println 1111)
;;             "hello world")
;;           (handler4 [context]
;;             (println 4444)
;;             (ct/delegate ))
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
