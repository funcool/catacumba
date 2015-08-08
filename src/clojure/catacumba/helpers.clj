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

(ns catacumba.helpers
  (:refer-clojure :exclude [promise])
  (:require [promissum.protocols :as pt])
  (:import ratpack.func.Action
           ratpack.func.Function
           ratpack.func.Block
           ratpack.exec.Promise
           ratpack.exec.Upstream
           ratpack.exec.Downstream
           ratpack.handling.Context
           java.util.concurrent.CompletableFuture
           io.netty.buffer.Unpooled))

(defn ^Action fn->action
  "Coerce a plain clojure function into
  ratpacks's Action interface."
  {:no-doc true}
  [callable]
  (reify Action
    (^void execute [_ x]
      (callable x))))

(defn ^Block fn->block
  "Coerce a plain clojure function into
  ratpacks's Block interface."
  {:no-doc true}
  [callable]
  (reify Block
    (execute [_]
      (callable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Promise & Async blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IPromiseAcceptor
  (^:private -accept [v ds]))

(extend-protocol IPromiseAcceptor
  CompletableFuture
  (-accept [f ^Downstream ds]
    (.accept ds f))

  Throwable
  (-accept [e ^Downstream ds]
    (.error ds e))

  Object
  (-accept [o ^Downstream ds]
    (.success ds o)))

(defn promise
  "A convenience function for create ratpack promises."
  [callback]
  (Promise/of (reify Upstream
                (^void connect [_ ^Downstream ds]
                  (let [continuation #(-accept % ds)]
                    (callback continuation))))))

(defmacro blocking
  "Performs a blocking operation on a separate thread,
  returning a promise for its value."
  [& body]
  `(Blocking/get
    (reify ratpack.func.Factory
      (create [_]
        ~@body))))

(defmacro async
  "Perform a async operation and return a promise.

  Warning: this function does not launch any additional
  thread, so is the user responsability does not
  call any blocking call inside the async block."
  [name & body]
  `(promise (fn [~name]
              ~@body)))

(defn then
  "A ratpack promise chain helper."
  [^Promise promise callback]
  (.then promise (fn->action callback)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bytebuffer coersions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IByteBuffer
  (bytebuffer [_] "Coerce to byte buffer."))

(extend-protocol IByteBuffer
  String
  (bytebuffer [s]
    (Unpooled/wrappedBuffer (.getBytes s "UTF-8"))))

