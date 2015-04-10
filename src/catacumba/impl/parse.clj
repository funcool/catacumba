(ns catacumba.impl.parse
  "Form parsing facilities."
  (:require [catacumba.utils :as utils])
  (:import ratpack.handling.Context
           ratpack.form.Form))

;; Implementation notes:
;; Reflection is used for access to private field of Form instance
;; because ratpack does not allows an other way for iter over
;; all parameters (including files) in an uniform way.

(defn parse
  "Parse form encoded or multipart request data and return
  a maybe multivalue map."
  [^Context context]
  (let [form (.parse context Form)
        field (.. form getClass (getDeclaredField "files"))
        _     (.setAccessible field true)
        files (.get field form)
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
