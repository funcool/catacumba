(ns catacumba.impl.helpers
  (:import ratpack.func.Action
           ratpack.func.Function
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


(defprotocol IByteBuffer
  (bytebuffer [_] "Coerce to byte buffer."))

(extend-protocol IByteBuffer
  String
  (bytebuffer [s]
    (Unpooled/wrappedBuffer (.getBytes s "UTF-8"))))
