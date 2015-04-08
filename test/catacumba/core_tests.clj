(ns catacumba.core-tests
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as http]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-kw-params]
            [catacumba.core :as ct]
            [catacumba.ratpack :as rp])
  (:import ratpack.registry.Registries))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-server [handler & body]
  `(let [server# (ct/run-server ~handler)]
     (try
       ~@body
       (finally (.stop server#)))))

(defmacro with-router-server [handler & body]
  `(let [handler# (-> ~handler
                      (with-meta {:type :ratpack-router}))
         server# (ct/run-server handler#)]
     (try
       ~@body
       (finally (.stop server#)))))

(def base-url "http://localhost:5050")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest handler-with-string-return
  (with-server (fn [_] "hello world")
    (let [response (http/get base-url)]
      (is (= (:body response) "hello world"))
      (is (= (:status response) 200)))))

(defn simple-chained
  [context params]
  (println "in simple-chained")
  (rp/delegate context {:data (rand-int 10000)}))

(defn hello-world-handler
  [context params]
  (let [data (rp/context-data context)]
    (println "in hello-world-handler" data)
    "hello world"))

(def myroutes
  (rp/routes [[:prefix "static"
               [:assets "public"]]
              [:prefix "foo"
               [:get ":name" simple-chained hello-world-handler]]]))

(deftest experiments
  (with-router-server myroutes
    (let [response (http/get (str base-url "/foo/bar"))
          response2 (http/get (str base-url "/foo/bar"))]
      (is (= (:body response) "hello world"))
      (is (= (:status response) 200)))))

(deftest experiments2
  (with-router-server myroutes
    (let [response (http/get (str base-url "/static/test.txt"))]
      (is (= (:body response) "hello world from test.txt\n"))
      (is (= (:status response) 200)))))
