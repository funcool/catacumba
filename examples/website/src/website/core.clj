(ns website.core
  (:require [clojure.java.io :as io]
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

;; Shows a simple html page. It has different content for anonynmous
;; and logged users.

(defn home-page
  [context]
  (-> (layout [:section {:class "home-page"}
               (if-let [user (:identity context)]
                 [:div
                  [:p (format "Welcome %s" (:username user))]
                  [:p [:a {:href "/logout"} "logout"]]]
                 [:div
                  [:p "Welcome to the dummy website application."]
                  [:p [:a {:href "/login"} "Login"]]])])
      (http/ok {:content-type "text/html"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Login page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A helper function for render login page, it used also for initial
;; rendering and render login page with errors on post requests.

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
       [:input {:type "text" :name "username"
                :placeholder "Write your username here..."}]]
      [:div {:class "input-wrapper"}
       [:input {:type "password" :name "password"
                :placeholder "Write your password here..."}]]
      [:div {:class "input-wrapper"}
       [:input {:type "submit" :value "Submit"}]]]])))


;; A simple function that has the responsability of authenticate the incomoning
;; credentials on login post request. In the current implementation it just
;; checks if a user and password matches to the builtin "user" representation,
;; but in your implementation this function may access to the database or any
;; other source for authenticate. This is just a example.

(defn- authenticate
  [username, password]
  (when (and (= username "admin")
             (= password "123123"))
    {:username "Admin"}))

;; A hanlder that simply render the login page for GET requests.

(defn login-page
  [context]
  (-> (render-login-page)
      (http/ok {:content-type "text/html"})))

;; A handler that clears the session and redirect to the home page.

(defn logout-page
  [context]
  (let [session (:session context)]
    (swap! session dissoc :identity)
    (http/found "/")))

;; A handler that handles the POST requests for login page.

(defn login-submit
  [context]
  (let [form-params (ct/parse-formdata context)
        query-params (ct/parse-queryparams context)
        username (get form-params "username" "")
        password (get form-params "password" "")]
    (if (or (empty? username) (empty? password))
      ;; Firstly validates the input, if required fields
      ;; are empty, render the login page html with
      ;; convenient error mesasage and return it.
      (http/ok (render-login-page ["The two fields are mandatory."])
               {:content-type "text/html"})

      ;; In case contrary, try validate the incoming
      ;; credentials, and in case of them validates
      ;; successful, update the session `:identity` key
      ;; with the authenticated user object.
      ;; In case contrary, return a login page
      ;; rendered with approapiate error message
      (if-let [user (authenticate username password)]
        (let [nexturl (get query-params "next" "/")
              session (:session context)]
          (swap! session assoc :identity user)
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

;; Define the application routes using `ct/routes`
;; function from catacumba.

(def app
  (ct/routes [[:prefix "assets"
               [:assets "resources/public"]]
              [:scope
               [:any (hs/session {:storage :inmemory})]
               [:any (hs/auth auth-backend)]
               [:get home-page]
               [:get "logout" logout-page]
               [:by-method "login"
                [:get login-page]
                [:post login-submit]]]]))

;; Define the main entry point that starts the catacumba
;; server un the port 5051.

(defn -main
  "The main entry point to your application."
  [& args]
  (ct/run-server app {:port 5051
                      :basedir "."}))
