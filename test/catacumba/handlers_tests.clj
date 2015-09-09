(ns catacumba.handlers-tests
  (:require [clojure.core.async :refer [put! take! chan <! >! go close!
                                        go-loop onto-chan timeout <!!] :as a]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as client]
            [buddy.sign.jws :as jws]
            [buddy.sign.jwe :as jwe]
            [buddy.core.hash :as hash]
            [slingshot.slingshot :refer [try+]]
            [cheshire.core :as json]
            [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.handlers :as hs]
            [catacumba.handlers.session :as session]
            [catacumba.handlers.auth :as auth]
            [catacumba.testing :as test-utils :refer [with-server]]
            [catacumba.core-tests :refer [base-url]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Body params parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest body-parsing
  (testing "Explicit body parsing with multipart request with multiple files."
    (let [p (promise)
          handler (fn [context]
                    (let [form (ct/get-formdata context)]
                      (deliver p form)
                      "hello world"))]
      (with-server {:handler handler}
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
            (is (= (get formdata :foo) "bar")))))))

  (testing "Form encoded body parsing using chain handler"
    (let [p (promise)
          app (ct/routes [[:any (hs/body-params)]
                          [:any #(do
                                   (deliver p (:data %))
                                   "hello world")]])]
      (with-server {:handler app}
        (let [response (client/post base-url {:form-params {:foo "bar"}})]
          (is (= {:foo "bar"} (deref p 1000 nil)))))))

  (testing "Json encoded body parsing using chain handler"
    (let [p (promise)
          app (ct/routes [[:any (hs/body-params)]
                          [:any #(do
                                   (deliver p (:data %))
                                   "hello world")]])]
      (with-server {:handler app}
        (let [response (client/post base-url {:body (json/generate-string {:foo "bar"})
                                              :content-type "application/json"})]
          (is (= {:foo "bar"} (deref p 1000 nil)))))))

  (testing "Transit encoded body parsing using chain handler"
    (let [p (promise)
          app (ct/routes [[:any (hs/body-params)]
                          [:any #(do
                                   (deliver p (:data %))
                                   "hello world")]])]
      (with-server {:handler app}
        (let [response (client/post base-url {:body (test-utils/data->transit {:foo/bar "bar"})
                                              :content-type "application/transit+json"})]
          (is (= {:foo/bar "bar"} (deref p 1000 nil)))))))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSRF
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest csrf-protect-tests
  (testing "Post with csrf using form param"
    (let [app (ct/routes [[:any (hs/csrf-protect)]
                          [:any (fn [context] "hello world")]])]
      (with-server {:handler app}
        (let [response (client/get base-url {:form-params {:foo "bar"
                                                           :csrftoken "baz"}
                                             :cookies {:csrftoken {:value "baz"}}})]
          (is (= (:status response) 200))))))

  (testing "Post with csrf using header"
    (let [app (ct/routes [[:any (hs/csrf-protect)]
                          [:any (fn [context] "hello world")]])]
      (with-server {:handler app}
        (let [response (client/get base-url {:form-params {:foo "bar"}
                                             :headers {:x-csrftoken "baz"}
                                             :cookies {:csrftoken {:value "baz"}}})]
          (is (= (:status response) 200))))))

  (testing "Post without csrf"
    (let [app (ct/routes [[:any (hs/csrf-protect)]
                          [:any (fn [context] "hello world")]])]
      (with-server {:handler app}
        (let [response (client/get base-url)]
          (is (= (:status response) 200)))
        (try+
         (let [response (client/post base-url {:form-params {:foo "bar"}})]
           (is (= (:status response) 400)))
         (catch [:status 400] {:keys [status]}
           (is (= status 400))))))))


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
      (with-server {:handler handler}
        (let [response (client/get base-url {:headers {:origin "http://localhost/"}})
              headers (:headers response)]
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (is (= (get headers "access-control-allow-origin") "*"))
          (is (= (get headers "access-control-allow-headers") "X-FooBar"))))))

  (testing "Options cors request"
    (let [handler (fn [ctx] "hello world")
          handler (ct/routes [[:any (hs/cors cors-config1)]
                              [:get handler]])]
      (with-server {:handler handler}
        (let [response (client/options base-url {:headers {:origin "http://localhost/"
                                                           :access-control-request-method "post"}})
              headers (:headers response)]
          (is (= (:body response) ""))
          (is (= (:status response) 200))
          (is (= (get headers "access-control-allow-origin") "*"))
          (is (= (get headers "access-control-max-age") "3600"))))))

  (testing "Wrong cors request"
    (let [handler (fn [ctx] "hello world")
          handler (ct/routes [[:any (hs/cors cors-config2)]
                              [:get handler]])]
      (with-server {:handler handler}
        (let [response (client/options base-url {:headers {"Origin" "http://localhast/"
                                                           "access-control-request-method" "post"}})
              headers (:headers response)]
          (is (= (:body response) ""))
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
          handler (ct/routes [[:any handler]])]
      (with-server {:handler handler}
        (let [response (client/get (str base-url "/foo"))
              ctx (deref p 1000 {})]
          (is (contains? ctx :body))
          (is (contains? ctx :query))
          (is (contains? ctx :query-params))
          (is (contains? ctx :route-params))
          (is (contains? ctx :headers))
          (is (contains? ctx :cookies))
          (is (contains? ctx :path))
          (is (contains? ctx :catacumba/request))
          (is (contains? ctx :catacumba/context))
          (is (contains? ctx :catacumba/response))
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200)))))))

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
          handler (ct/routes [[:any (hs/session)]
                              [:any handler]])]
      (with-server {:handler handler}
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

  ;; (testing "In memory session storage"
  ;;   (let [st (session/memory-storage)]
  ;;     (is (nil? (#'session/read-session st :foo)))
  ;;     (#'session/write-session st :foo {:bar 2})
  ;;     (is (= (#'session/read-session st :foo) {:bar 2}))))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest interceptors-tests
  (let [counter (atom 0)
        p1 (promise)]
    (letfn [(interceptor [_ type continuation]
              (swap! counter inc)
              (continuation)
              (swap! counter inc))
            (handler2 [context]
              (ct/delegate context))
            (handler3 [context]
              (deliver p1 nil)
              "hello world")]
      (with-server {:handler (ct/routes [[:interceptor interceptor]
                                         [:any handler2]
                                         [:any handler3]])}
        (let [response (client/get base-url)]
          (is (nil? (deref p1 1000 nil)))
          (is (= (:body response) "hello world"))
          (is (= (:status response) 200))
          (is (= @counter 2)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Auth
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def jws-secret "secret")
(def jwe-secret (hash/sha256 jws-secret))

(def jws-backend (auth/jws-backend {:secret jws-secret}))
(def jwe-backend (auth/jwe-backend {:secret jwe-secret}))

(deftest auth-tests
  (testing "JWS"
    (letfn [(handler [context]
              (if (:identity context)
                (http/ok "Identified")
                (http/unauthorized "Unauthorized")))]
      (with-server {:handler (ct/routes [[:auth jws-backend]
                                         [:any handler]])}
        (try+
         (let [response (client/get base-url)]
           (is (= (:status response) 401)))
         (catch Object e
           (is (= (:status e) 401))))

        (let [token (jws/sign {:userid 1} jws-secret)
              headers {"Authorization" (str "Token " token)}
              response (client/get base-url {:headers headers})]
          (is (= (:status response) 200))))))

  (testing "JWE"
    (letfn [(handler [context]
              (if (:identity context)
                (http/ok "Identified")
                (http/unauthorized "Unauthorized")))]
      (with-server {:handler (ct/routes [[:auth jwe-backend]
                                         [:any handler]])}
        (try+
         (let [response (client/get base-url)]
           (is (= (:status response) 401)))
         (catch Object e
           (is (= (:status e) 401))))

        (let [token (jwe/encrypt {:userid 1} jwe-secret)
              headers {"Authorization" (str "Token " token)}
              response (client/get base-url {:headers headers})]
          (is (= (:status response) 200)))))))
