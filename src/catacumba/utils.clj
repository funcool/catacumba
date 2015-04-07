(ns catacumba.utils
  (:import ratpack.func.Action
           ratpack.func.Function))

(def ^{:doc "Transducer for lowercase headers keys."}
  lowercase-keys-t (map (fn [[^String key value]]
                          [(.toLowerCase key) value])))

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

