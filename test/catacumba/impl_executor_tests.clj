(ns catacumba.impl-executor-tests
  (:require [clojure.core.async :as a]
            [clojure.test :refer :all]
            [catacumba.impl.executor :as exec]))

(deftest iexecutor-tests
  (let [p (promise)]
    (exec/execute #(deliver p true))
    (is (deref p 1000 false)))

  (let [p (promise)]
    (exec/execute exec/default #(deliver p true))
    (is (deref p 1000 false))))

(deftest iexecutor-service-tests
  (let [p (exec/submit (fn []
                         (a/<!! (a/timeout 500))
                         true))]
    (is (deref p 1000 false)))
  (let [submit (partial exec/submit exec/default)
        p (submit (fn []
                    (a/<!! (a/timeout 500))
                    true))]
    (is (deref p 1000 false))))

