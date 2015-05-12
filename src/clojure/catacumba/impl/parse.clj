(ns catacumba.impl.parse
  "Form parsing facilities."
  (:require [catacumba.utils :as utils])
  (:import ratpack.handling.Context
           ratpack.form.Form
           ratpack.http.Request
           ratpack.util.MultiValueMap
           catacumba.impl.context.DefaultContext))

;; Implementation notes:
;; Reflection is used for access to private field of Form instance
;; because ratpack does not allows an other way for iter over
;; all parameters (including files) in an uniform way.

(defn- extract-files
  [^Form form]
  (let [field (.. form getClass (getDeclaredField "files"))]
    (.setAccessible field true)
    (.get field form)))

(defn parse-queryparams
  "Parse query params from request and return a
  maybe multivalue map."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^Request request (.getRequest ctx)
        ^MultiValueMap params (.getQueryParams request)]
    (persistent!
     (reduce (fn [acc key]
               (let [values (.getAll params key)]
                 (reduce #(utils/assoc-conj! %1 key %2) acc values)))
             (transient {})
             (.keySet params)))))

(defn parse-formdata
  "Parse form encoded or multipart request data and return
  a maybe multivalue map."
  [^DefaultContext context]
  (let [^Context ctx (:catacumba/context context)
        ^MultiValueMap form (.parse ctx Form)
        ^MultiValueMap files (extract-files form)
        result (transient {})]
    (reduce (fn [acc key]
              (let [values (.getAll files key)]
                (reduce #(utils/assoc-conj! %1 key %2) acc values)))
            result
            (.keySet files))
    (reduce (fn [acc key]
              (let [values (.getAll form key)]
                (reduce #(utils/assoc-conj! %1 key %2) acc values)))
            result
            (.keySet form))
    (persistent! result)))
