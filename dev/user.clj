(ns user
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [clojure.test :as test]))

(defn test
  ([] (test #"^catacumba.tests.*"))
  ([o]
   (repl/refresh)
   (cond
     (instance? java.util.regex.Pattern o)
     (test/run-all-tests o)

     (symbol? o)
     (if-let [sns (namespace o)]
       (do (require (symbol sns))
           (test/test-vars [(resolve o)]))
       (test/test-ns o)))))

