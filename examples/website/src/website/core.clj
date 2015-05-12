(ns website.core
  (:require [clojure.java.io :as io]
            ;; [hiccup.core :as hc]
            [hiccup.page :as hc]
            [catacumba.core :as ct]
            [catacumba.handlers :as hs]
            [catacumba.handlers.auth :as auth]
            [catacumba.http :as http])
  (:gen-class))

;; A function that renders the basic html layout
;; for all pages used in that application.

(defn layout
  [content]
  (hc/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:title "Sample dummy website"]
    [:link {:href "/assets/styles.css"
            :type "text/css"
            :rel "stylesheet"
            :media "screen"}]]
   [:body content]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Home Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Shows a simple html with two links, one to restricted area and
;; an other directly to the login. If you are go directly to the
;; restricted area without to be authenticated, you will be redirected
;; to the login page.

(defn home-page
  [context]
  (-> (layout [:section {:class "home-page"}
               [:p "Welcome to the dummy website application."]
               [:p [:a {:href "/restricted"} "Restricted Area"]]
               [:p [:a {:href "/login"} "Login"]]])
      (http/ok {:content-type "text/html"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Login page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- render-login-page
  ([] (render-login-page []))
  ([errors]
   (layout
    [:section {:class "login-page"}
     [:p "Login"]
     (when (seq errors)
       [:div {:class "errors"}
        (for [e errors] [:div e])])
     [:form {:action "" :method "post"}
      [:div {:class "input-wrapper"}
       [:input {:type "text" :name "username" :placeholder "Write your username here..."}]]
      [:div {:class "input-wrapper"}
       [:input {:type "password" :name "password" :placeholder "Write your password here..."}]]
      [:div {:class "input-wrapper"}
       [:input {:type "submit" :value "Submit"}]]]])))


(defn- authenticate
  [username, password]
  (when (and (= username "admin")
             (= password "123123"))
    {:userid 1}))

(defn login-page
  [context]
  (-> (render-login-page ["foo", "bar"])
      (http/ok {:content-type "text/html"})))

(defn login-submit
  [context]
  (let [form-params (ct/parse-formdata context)
        query-params (ct/parse-queryparams context)
        username (get form-params "username" "")
        password (get form-params "password" "")]
    (if (or (empty? username) (empty? password))
      (http/ok (render-login-page ["The two fields are mandatory."])
               {:content-type "text/html"})
      (if-let [user (authenticate username password)]
        (let [nexturl (get query-params "next" "/")]
          (http/found nexturl))
        (http/ok (render-login-page ["User or password are incorrect"])
                 {:content-type "text/html"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Define a authentication backend. In this case we will use a session
;; based authentication backend because we are building a website and
;; session auth backend is the most appropiate for this case.

(def auth-backend
  (auth/session-backend))

(def app
  (ct/routes [[:any (hs/session {:storage :inmemory})]
              [:any (hs/auth auth-backend)
              [:get home-page]
              [:by-method "login"
               [:get login-page]
               [:post login-submit]]
              [:prefix "assets"
               [:assets "resources/public"]]]))

;; Start the server using the `run-server` function.
;; This has one peculiarity, the `:registry` property. It
;; is used for setup contextual objects and in this case
;; the function `session-decorator` returns a handler
;; decorator that it should be attached to the registry

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server app {:port 5051
                      :basedir "."}))
