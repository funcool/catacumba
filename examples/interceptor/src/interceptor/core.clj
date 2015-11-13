(ns interceptor.core
  (:require [catacumba.core :as ct]
            [catacumba.http :as http]
            [catacumba.handlers.misc])
  (:gen-class))

(defn index
  [context]
  (http/ok "<strong>Hello World</strong>"
           {:content-type "text/html; encoding=utg-8"}))


(defn- time-interceptor
  [exc type cont]
  (let [st (System/nanoTime)]
    (cont)
    (let [et (System/nanoTime)
          elapsed (- et st)
          elapsed (/ elapsed 1000000000.0)]
      (println (format "Computation %s elapsed in: %s (sec)" type elapsed)))))

(def app
  (ct/routes [[:interceptor time-interceptor]
              [:get index]]))

(defn -main
  [& args]
  (ct/run-server app))
