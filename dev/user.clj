(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [clojure.test :as test]))


(defonce ^:dynamic
  *namespaces*
  ['catacumba.core-tests
   'catacumba.sse-tests
   'catacumba.ring-tests
   'catacumba.handlers-tests
   'catacumba.websocket-tests])

(defn run-tests'
  []
  (apply test/run-tests *namespaces*))

(defn run-tests
  [& nss]
  (if (pos? (count nss))
    (binding [*namespaces* nss]
      (repl/refresh :after 'user/run-tests'))
    (repl/refresh :after 'user/run-tests')))
