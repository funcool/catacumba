(ns catacumba.core-tests
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [clj-http.client :as http]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-kw-params]
            [catacumba.core :as ct]
            [catacumba.ratpack :as rp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-server [handler & body]
  `(let [server# (ct/run-server ~handler)]
     (try
       ~@body
       (finally (.stop server#)))))

;; (defn hello-world-handler
;;   [ctx]
;;   "hello world")

(def base-url "http://localhost:5050")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest handler-with-string-return
  (with-server (fn [_] "hello world")
    (let [response (http/get base-url)]
      (is (= (:body response) "hello world"))
      (is (= (:status response) 200)))))

