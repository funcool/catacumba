;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
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

(ns catacumba.impl.helpers
  (:refer-clojure :exclude [promise])
  (:require [clojure.core.async :as a])
  (:import ratpack.func.Action
           ratpack.func.Function
           ratpack.func.Block
           ratpack.exec.Promise
           ratpack.exec.Upstream
           ratpack.exec.Downstream
           ratpack.exec.Blocking
           ratpack.handling.Context
           java.nio.file.Path
           java.nio.file.Paths
           java.util.concurrent.CompletableFuture
           io.netty.buffer.Unpooled))

;; --- Java 8 Interop

(defn fn->action
  "Coerce a plain clojure function into
  ratpacks's Action interface."
  ^Action [callable]
  (reify Action
    (^void execute [_ x]
      (callable x))))

(defn fn->function
  "Coerce a plain clojure function into
  ratpacks's Function interface."
  ^Function [callable]
  (reify Function
    (apply [_ x]
      (callable x))))

(defn fn->block
  "Coerce a plain clojure function into
  ratpacks's Block interface."
  ^Block [callable]
  (reify Block
    (execute [_]
      (callable))))

;; --- Promise & Async blocks

(defprotocol IPromiseAcceptor
  (-accept [v ds]))

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
                  (let [accept #(-accept % ds)]
                    (callback accept))))))

(defn completable-future->promise
  "Coerce jdk8 completable future to ratpack promise."
  [fut]
  (promise (fn [accept]
             (accept fut))))

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

;; --- Bytebuffer coersions.

(defprotocol IByteBuffer
  (bytebuffer [_] "Coerce to byte buffer."))

(extend-protocol IByteBuffer
  String
  (bytebuffer [s]
    (Unpooled/wrappedBuffer (.getBytes s "UTF-8"))))

;; --- Common Transducers

(def lowercase-keys-t
  (map (fn [[^String key value]]
         [(.toLowerCase key) value])))

(def keywordice-keys-t
  (map (fn [[^String key value]]
         [(keyword key) value])))

;; --- Path Helpers

(defprotocol IPath
  (^:private -to-path [_]))

(extend-protocol IPath
  Path
  (-to-path [v] v)

  String
  (-to-path [v]
    (Paths/get v (into-array String []))))

(defn to-path
  {:internal true :no-doc true}
  [value]
  (-to-path value))

;; --- Exceptions

(defmacro try-on
  [& body]
  `(try (do ~@body) (catch Throwable e# e#)))

;; --- Aliases

(defmacro defalias
  [sym sym2]
  `(do
     (def ~sym ~sym2)
     (alter-meta! (var ~sym) merge (dissoc (meta (var ~sym2)) :name))))
