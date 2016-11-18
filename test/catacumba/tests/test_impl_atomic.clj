(ns catacumba.tests.test-impl-atomic
  (:require [clojure.test :refer :all]
            [catacumba.impl.atomic :as atomic]))

(deftest atomic-long
  (testing "Constructor"
    (let [v (atomic/long 0)]
      (is (satisfies? atomic/IAtomic v))
      (is (satisfies? atomic/IAtomicNumber v))))

  (testing "CAS operations"
    (let [v (atomic/long 0)]
      (is (atomic/compare-and-set! v 0 1))
      (is (= @v 1))))

  (testing "Get and Set"
    (let [v (atomic/long 0)]
      (is (= 0 (atomic/get-and-set! v 1)))
      (is (= @v 1))))

  (testing "Set"
    (let [v (atomic/long 0)]
      (atomic/set! v 1)
      (is (= @v 1))))

  (testing "Get and inc"
    (let [v (atomic/long 0)]
      (is (= 0 (atomic/get-and-inc! v)))
      (is (= @v 1))))

  (testing "Get and dec"
    (let [v (atomic/long 0)]
      (is (= 0 (atomic/get-and-dec! v)))
      (is (= @v -1))))

  (testing "Get and add"
    (let [v (atomic/long 0)]
      (is (= 0 (atomic/get-and-add! v 5)))
      (is (= @v 5))))
)

(deftest atomic-bool
  (testing "Constructor"
    (let [v (atomic/boolean false)]
      (is (satisfies? atomic/IAtomic v))
      (is (not (satisfies? atomic/IAtomicNumber v)))))

  (testing "CAS operations"
    (let [v (atomic/boolean false)]
      (is (atomic/compare-and-set! v false true))
      (is (= @v true))))

  (testing "Get and Set"
    (let [v (atomic/boolean false)]
      (is (= false (atomic/get-and-set! v true)))
      (is (= @v true))))

  (testing "Set"
    (let [v (atomic/boolean false)]
      (atomic/set! v true)
      (is (= @v true))))
)
