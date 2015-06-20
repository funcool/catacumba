(ns catacumba.testing
  "Testing facilities for catacuba."
  (:require [catacumba.core :as ct]))

(defmacro with-server
  "Evaluate code in context of running catacumba server."
  [{:keys [handler sleep] :or {sleep 50} :as options} & body]
  `(let [options# (merge {:basedir "."} ~options)
         server# (ct/run-server ~handler options#)]
     (try
       ~@body
       (finally
         (.stop server#)
         (Thread/sleep ~sleep)))))
