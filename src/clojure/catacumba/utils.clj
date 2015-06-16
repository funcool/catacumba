(ns catacumba.utils
  (:import java.nio.file.Paths))

(def ^{:doc "Transducer for lowercase headers keys."}
  lowercase-keys-t (map (fn [[^String key value]]
                          [(.toLowerCase key) value])))

(def ^{:doc "Transducer for keywordice."}
  keywordice-keys-t (map (fn [[^String key value]]
                           [(keyword key) value])))

(defn str->path
  [^String path]
  (Paths/get path (into-array String [])))

(defn assoc-conj!
  [map key val]
  (assoc! map key
    (if-let [cur (get map key)]
      (if (vector? cur)
        (conj cur val)
        [cur val])
      val)))

(defn- get-arities
  [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (->> (class f)
       (.getDeclaredMethods)
       (filter #(= "invoke" (.getName %)))
       (map #(-> % .getParameterTypes alength))
       (set)))
