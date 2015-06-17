(ns catacumba.handlers.autoreload
  "Autoreload chain handler.

  NOTE: this should be only used in development mode."
  (:require [ns-tracker.core :refer [ns-tracker]]
            [catacumba.impl.context :as ct]))

(defn autoreloader
  ([] (autoreloader {}))
  ([{:keys [dirs] :or {dirs ["src"]}}]
   (let [tracker (ns-tracker dirs)]
     (fn [context]
       (doseq [ns-sym (tracker)]
         (println "=> reload:" ns-sym)
         (require ns-sym :reload))
       (ct/delegate context)))))
