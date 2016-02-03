(ns website.core
  (:require [catacumba.core :as ct]
            [catacumba.handlers.misc :as misc]
            [catacumba.handlers.auth :as cauth]
            [catacumba.handlers.session :as cses]
            [website.handlers :as handlers])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Define a authentication backend. In this case we will use a session
;; based authentication backend because we are building a website and
;; session auth backend is the most appropiate for this case.

(def auth-backend
  (cauth/session-backend))

;; Define the application routes using `ct/routes`
;; function from catacumba.

(def app
  (ct/routes [[:assets "assets" {:dir "public"}]
              [:any (misc/autoreloader)]
              [:any (cses/session {:storage :inmemory})]
              [:any (cauth/auth auth-backend)]
              [:get #'handlers/home-page]
              [:get "logout" #'handlers/logout-page]
              [:prefix "login"
               [:by-method {:get #'handlers/login-page
                            :post #'handlers/login-submit}]]]))

;; Define the main entry point that starts the catacumba
;; server un the port 5051.

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server app {:port 5050}))
