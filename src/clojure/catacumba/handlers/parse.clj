(ns catacumba.handlers.parse
  (:require [catacumba.impl.context :as context]
            [catacumba.impl.parse :as parse]
            [catacumba.impl.handlers :as handlers]
            [cheshire.core :as json])
  (:import ratpack.http.Request
           ratpack.http.TypedData
           ratpack.handling.Context))

(defmulti parse
  "A polymorophic method for parse body into clojure
  friendly data structure."
  (fn [^Context ctx ^TypedData body]
    (let [^Request request (.getRequest ctx)
          ^String contenttype (.. request getBody getContentType getType)]
      (if contenttype
        (keyword (.toLowerCase contenttype))
        :application/octet-stream)))
  :default :application/octet-stream)

(defmethod parse :multipart/form-data
  [^Context ctx ^TypedData body]
  (parse/parse-formdata* ctx))

(defmethod parse :application/x-www-form-urlencoded
  [^Context ctx ^TypedData body]
  (parse/parse-formdata* ctx))

(defmethod parse :application/json
  [^Context ctx ^TypedData body]
  (let [^String data (slurp body)]
    (json/parse-string data true)))

(defmethod parse :application/octet-stream
  [^Context ctx ^TypedData body]
  nil)

(defn body-params
  "A route chain that parses the body into
  a clojure friendly data structure.

  This function optionally accept a used defined method
  or multimethod where to delegate the body parsing."
  ([] (body-params parse))
  ([parsefn]
   (fn [context]
     (let [^Context ctx (:catacumba/context context)
           ^TypedData body (.. ctx getRequest getBody)]
       (context/delegate context {:body (parsefn ctx body)})))))
