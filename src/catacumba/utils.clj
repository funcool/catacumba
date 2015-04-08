(ns catacumba.utils
  (:import ratpack.func.Action
           ratpack.func.Function
           java.nio.file.Paths))

(def ^{:doc "Transducer for lowercase headers keys."}
  lowercase-keys-t (map (fn [[^String key value]]
                          [(.toLowerCase key) value])))

(def ^{:doc "Transducer for keywordice."}
  keywordice-keys-t (map (fn [[^String key value]]
                           [(keyword key) value])))

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

(defn str->path
  [^String path]
  (Paths/get path (into-array String [])))
