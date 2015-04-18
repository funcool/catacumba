(ns catacumba.impl.helpers
  (:import ratpack.func.Action
           ratpack.func.Function))

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

