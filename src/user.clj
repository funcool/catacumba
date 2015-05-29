(ns user
  (:require [clojure.tools.namespace.repl :refer (refresh)]
            [clojure.test :as test]))
;; [catacumba.core :as ct]))

;; (defn test-ns
;;   ([]
;;    (refresh)
;;    (test/run-tests
;;     'catacumba.core-tests
;;     'catacumba.sse-tests
;;     'catacumba.ring-tests
;;     'catacumba.handlers-tests
;;     'catacumba.websocket-tests
;;     ))
;;   ([& namespaces]
;;    (refresh)
;;    (apply test/run-tests namespaces)))

;; (defn test-vars
;;   [& vars]
;;   (refresh)
;;   (test/test-vars vars))
