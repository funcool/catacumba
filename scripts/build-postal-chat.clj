(require '[cljs.build.api :as b])

(println "Building ...")

(let [start (System/nanoTime)]
  (b/build
   (b/inputs "examples/postal-chat/src/cljs")
   {:output-to "examples/postal-chat/resources/public/js/app.js"
    :output-dir "examples/postal-chat/resources/public/js"
    :asset-path "js"
    :optimizations :none
    :main 'compchat.client
    :pretty-print true
    :language-in  :ecmascript5
    :language-out :ecmascript5
    :verbose true})
  (println "... done. Elapsed" (/ (- (System/nanoTime) start) 1e9) "seconds"))
