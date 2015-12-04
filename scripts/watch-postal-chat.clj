(require '[cljs.build.api :as b])

(b/watch
 (b/inputs "examples/postal-chat/src/cljs")
 {:main 'compchat.client
  :output-to "examples/postal-chat/resources/public/js/app.js"
  :output-dir "examples/postal-chat/resources/public/js"
  :asset-path "js"
  :pretty-print true
  :language-in  :ecmascript5
  :language-out :ecmascript5
  :verbose true})
