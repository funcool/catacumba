(ns website.core
  (:require [catacumba.core :as ct]
            [catacumba.handlers :as hs]
            [catacumba.handlers.auth :as auth]
            [website.handlers :as handlers])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Define a authentication backend. In this case we will use a session
;; based authentication backend because we are building a website and
;; session auth backend is the most appropiate for this case.

(def auth-backend
  (auth/session-backend))

;; Define the application routes using `ct/routes`
;; function from catacumba.

(def app
  (ct/routes [[:assets "assets" {:dir "public"}]
              [:any (hs/autoreloader)]
              [:any (hs/session {:storage :inmemory})]
              [:any (hs/auth auth-backend)]
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
  (ct/run-server app {:port 5050
                      :keystore-path "ssl/server.p12"
                      :keystore-secret "test"}))
