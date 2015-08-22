(ns bench
  (:require [catacumba.core :as ct]
            [catacumba.http :as http])
  (:gen-class))

(def bench-app1
  (letfn [(handler1 [context]
            (ct/delegate {:foo 1}))
          (handler2 [context]
            (ct/delegate {:bar 1}))
          (handler3 [context]
            (ct/delegate {:baz (str (:foo context) "-" (:bar context))}))
          (handler4 [context]
            (let [info (:baz context)]
              (http/ok info {:content-type "text/plain; charset=utf-8"})))]
    (ct/routes [[:any handler1]
                [:any handler2]
                [:any handler3]
                [:any handler4]])))

(defn -main
  [& args]
  (ct/run-server bench-app1 {:debug true}))
