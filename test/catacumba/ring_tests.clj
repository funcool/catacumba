(ns catacumba.ring-tests
  "Tests for ring compatibility layer on top
  of ratpack handlers."
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as http]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-kw-params]
            [catacumba.core :as ct]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-ring-server [handler & body]
  `(let [handler# (-> ~handler
                      ring-kw-params/wrap-keyword-params
                      ring-params/wrap-params)
         handler# (with-meta handler# {:type :ring})
         server# (ct/run-server handler#)]
     (try
       ~@body
       (finally (.stop server#)))))

(defn echo-handler
  [request]
  {:status 200
   :headers {"request-map" (pr-str (dissoc request :body))}
   :body (:body request)})

(defn content-type-handler
  [content-type]
  (fn [request]
    {:status  200
     :headers {"Content-Type" content-type}
     :body    ""}))

(defn read-request
  [response]
  (-> response
      (get-in [:headers "request-map"])
      (read-string)))

(def base-url "http://localhost:5050")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest request-get
  (with-ring-server echo-handler
    (let [response (http/get base-url {:basic-auth ["user" "password"]})
          request-map (read-request response)]
      (is (= (:request-method request-map) :get))
      (is (= (get-in request-map [:headers "authorization"])
             "Basic dXNlcjpwYXNzd29yZA==")))))

(deftest request-post
  (with-ring-server echo-handler
    (let [response (http/post base-url {:body "foobar"})
          request-map (read-request response)]
      (is (= (:request-method request-map) :post))
      (is (= "foobar" (:body response))))))

(deftest request-put
  (with-ring-server echo-handler
    (let [response (http/put base-url {:body "foobar"})
          request-map (read-request response)]
      (is (= (:request-method request-map) :put))
      (is (= "foobar" (:body response))))))

(deftest request-translate
  (with-ring-server echo-handler
    (let [url (str base-url "/foo/bar/baz?surname=jones&age=123")
          response (http/post url {:body "hello"})]
      (is (= (:status response) 200))
      (is (= (:body response) "hello"))
      (let [request-map (read-request response)]
        (is (= (:query-string request-map) "surname=jones&age=123"))
        (is (= (:uri request-map) "/foo/bar/baz"))
        (is (= (:content-length request-map) 5))
        (is (= (:character-encoding request-map) "UTF-8"))
        (is (= (:request-method request-map) :post))
        (is (= (:content-type request-map) "text/plain"))
        (is (= (:remote-addr request-map) "127.0.0.1"))
        (is (= (:scheme request-map) :http))
        (is (= (:server-name request-map) "127.0.0.1"))
        (is (= (:server-port request-map) 5050))
        (is (= (:ssl-client-cert request-map) nil))))))

(deftest custom-content-type
  (with-ring-server (content-type-handler "text/plain;charset=UTF-16;version=1")
    (let [response (http/get base-url)]
      (is (= (get-in response [:headers "content-type"])
             "text/plain;charset=UTF-16;version=1")))))
