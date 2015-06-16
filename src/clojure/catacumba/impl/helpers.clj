(ns catacumba.impl.helpers
  (:import ratpack.func.Action
           ratpack.func.Function
           ratpack.handling.Context
           io.netty.buffer.Unpooled))

(defn action
  "Coerce a plain clojure function into
  ratpacks's Action interface."
  [callable]
  (reify Action
    (^void execute [_ x]
      (callable x))))

(defn function
  "Coerce a plain clojure function into
  ratpacks's Function interface."
  [callable]
  (reify Function
    (apply [_ x]
      (callable x))))

(defn promise
  "A convenience function for create ratpack
  promises from context instance."
  [^Context ctx callback]
  (.promise ctx (action callback)))

(defprotocol IByteBuffer
  (bytebuffer [_] "Coerce to byte buffer."))

(extend-protocol IByteBuffer
  String
  (bytebuffer [s]
    (Unpooled/wrappedBuffer (.getBytes s "UTF-8"))))
