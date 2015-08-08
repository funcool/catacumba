;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are met:
;;
;; * Redistributions of source code must retain the above copyright notice, this
;;   list of conditions and the following disclaimer.
;;
;; * Redistributions in binary form must reproduce the above copyright notice,
;;   this list of conditions and the following disclaimer in the documentation
;;   and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
;; AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
;; IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;; DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
;; FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
;; DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
;; SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;; CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
;; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.handlers.interceptor
  "Helpers for intercept execution of handlers chain.
  Primarily for traceability and recording metrics."
  (:require [catacumba.impl.routing :as routing]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.context :as context])
  (:import ratpack.handling.Chain
           ratpack.handling.Context
           ratpack.handling.Handler
           ratpack.exec.Execution
           ratpack.func.Block
           ratpack.exec.ExecInterceptor
           ratpack.exec.ExecInterceptor$ExecType))

(defn- exec-interceptor
  [interceptor]
  (reify ExecInterceptor
    (^void intercept [_ ^Execution exc ^ExecInterceptor$ExecType t ^Block b]
      (let [continuation #(.execute b)
            exectype (if (= t ExecInterceptor$ExecType/BLOCKING)
                       :blocking
                       :compute)]
        (interceptor exc exectype continuation)))))

(defn interceptor
  "Start interceptor from current context.

  It wraps the rest of route chain the execution. It receive a
  continuation (as a cloure function) that must be called in
  order for processing to proceed."
  [context interceptor]
  (let [^Context ctx (:catacumba/context context)
        ^Execution exec (.getExecution ctx)]
    (.addInterceptor exec
                     (exec-interceptor interceptor)
                     (reify Block
                       (^void execute [_]
                         (context/delegate context))))))

(defmethod routing/attach-route :interceptor
  [^Chain chain [_ interceptor']]
  (let [handler #(interceptor % interceptor')]
    (.all chain ^Handler (handlers/adapter handler))))
