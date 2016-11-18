(ns catacumba.tests.helpers
  (:refer-clojure :exclude [get])
  (:require [clj-http.client :as http]
            [cuerdas.core :as str]
            [buddy.core.codecs :as codecs]))

;; --- HTTP Helpers

(def base-url "http://localhost:5050")

(defn- adapt-url
  [url]
  (if (str/starts-with? url "http") url (str base-url url)))

(defn- request
  [method url & args]
  (let [url (adapt-url url)
        callable (case method
                   :post http/post
                   :get http/get
                   :put http/put
                   :delete http/delete
                   :options http/options)]
    (try
      (apply callable url args)
      (catch clojure.lang.ExceptionInfo e
        (ex-data e)))))

(def post (partial request :post))
(def get (partial request :get))
(def put (partial request :put))
(def delete (partial request :delete))
(def options (partial request :options))
