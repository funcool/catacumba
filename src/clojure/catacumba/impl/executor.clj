;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions
;; are met:
;;
;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.
;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
;; IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
;; OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
;; IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
;; INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
;; NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
;; THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.impl.executor
  "A basic abstraction for executor services."
  (:require [promesa.core :as p])
  (:import java.util.concurrent.ForkJoinPool
           java.util.concurrent.Executor
           java.util.concurrent.Executors
           java.util.concurrent.ThreadFactory))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The main abstraction definition.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IExecutor
  (^:private execute* [_ task] "Execute a task in a executor."))

(defprotocol IExecutorService
  (^:private submit* [_ task] "Submit a task and return a promise."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type Executor
  IExecutor
  (execute* [this task]
    (.execute this ^Runnable task))

  IExecutorService
  (submit* [this task]
    (p/promise
     (fn [resolve reject]
       (execute* this #(try
                         (resolve (task))
                         (catch Throwable e
                           (reject e))))))))

(defn- thread-factory-adapter
  "Adapt a simple clojure function into a
  ThreadFactory instance."
  [func]
  (reify ThreadFactory
    (^Thread newThread [_ ^Runnable runnable]
      (func runnable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *default* (ForkJoinPool/commonPool))
(def ^:dynamic *default-thread-factory* (Executors/defaultThreadFactory))

(defn fixed
  "A fixed thread pool constructor."
  ([n]
   (Executors/newFixedThreadPool n *default-thread-factory*))
  ([n factory]
   (Executors/newFixedThreadPool n (thread-factory-adapter factory))))

(defn single-thread
  "A single thread executor constructor."
  ([]
   (Executors/newSingleThreadExecutor *default-thread-factory*))
  ([factory]
   (Executors/newSingleThreadExecutor (thread-factory-adapter factory))))

(defn cached
  "A cached thread executor constructor."
  ([]
   (Executors/newCachedThreadPool *default-thread-factory*))
  ([factory]
   (Executors/newCachedThreadPool (thread-factory-adapter factory))))

(defn execute
  "Execute a task in a provided executor.

  A task is a plain clojure function or
  jvm Runnable instance."
  ([task]
   (execute* *default* task))
  ([executor task]
   (execute* executor task)))

(defn submit
  "Submit a task to be executed in a provided executor
  and return a promise that will be completed with
  the return value of a task.

  A task is a plain clojure function."
  ([task]
   (submit* *default* task))
  ([executor task]
   (submit* executor task)))

