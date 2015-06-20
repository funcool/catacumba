(ns catacumba.handlers.security
  (:require [catacumba.impl.context :as ct]
            [catacumba.impl.parse :as ps]
            [catacumba.impl.handlers :as hs]
            [catacumba.http :as http]
            [cuerdas.core :as str]
            [clj-uuid :as uuid])
  (:import ratpack.http.Response))

(defn hsts-headers
  "A chain handler that adds the Strict-Transport-Security
  header to the response. This accepts the following params:

  - `max-age`: the max time in seconds for the policy (default: 1 year)
  - `subdomains`: include subdomains or not (default true)

  For more information, see the following rfc: https://tools.ietf.org/html/rfc6797"
  ([] (hsts-headers {}))
  ([{:keys [max-age subdomains] :or {max-age 31536000 subdomains true}}]
   (fn [context]
     (let [header-value (str "max-age=" max-age (when subdomains "; includeSubDomains"))]
       (hs/set-headers! context {"Strict-Transport-Security" header-value})
       (ct/delegate context)))))

(defn frame-options-headers
  "A chain handler that adds X-Frame-Options header to the response.

  The X-Frame-Options HTTP response header can be used to indicate
  whether or not a browser should be allowed to render a page in a `<frame>`,
  `<iframe>` or `<object>` . Sites can use this to avoid clickjacking attacks,
  by ensuring that their content is not embedded into other sites.

  Possible options:

  - `:policy`: with two possible values `:deny` and `:sameorigin`

  Warning: The frame-ancestors directive from the CSP Level 2 specification
  officially replaces this non-standard header."
  ([] (frame-options-headers {}))
  ([{:keys [policy] :or {policy :sameorigin}}]
   {:pre [(or (= policy :sameorigin)
              (= policy :deny))]}
   (let [header-value (str/upper (name policy))]
     (fn [context]
       (hs/set-headers! context {"X-Frame-Options" header-value})
       (ct/delegate context)))))

(defn csp-headers
  "A chain handler that adds Content-Security-Policy header to the response.

  Content Security Policy (CSP) is an added layer of security that helps to
  detect and mitigate certain types of attacks, including Cross Site Scripting
  (XSS) and data injection attacks. These attacks are used for everything from
  data theft to site defacement or distribution of malware.

  Example:

      (def cspconf {:default-src \"'self' *.trusted.com\"
                    :img-src \"*\"
                    :frame-ancestors \"'none'\"
                    :reflected-xss \"filter\"})

      (def app
        (ct/routes [[:prefix \"web\"
                     [:all (csp-headers cspconf)]
                     [:get your-handler]]]))

  You can read more about that here:
  https://developer.mozilla.org/en-US/docs/Web/Security/CSP"
  ([] (csp-headers {}))
  ([options]
   (let [options' (select-keys options :default-src :frame-ancestors :frame-src
                               :child-src :connect-src :font-src :form-action
                               :img-src :media-src :object-src :reflected-xss)
         value (reduce (fn [acc [key value]]
                         (conj acc (str (name key) value)))
                       [] (seq options'))]
     (assert (pos? (count value)))
     (fn [context]
       (hs/set-headers! context {"Content-Security-Policy" (str/join "; " value)})
       (ct/delegate context)))))

(defn content-type-options-headers
  "A chain handler that adds the `X-Content-Type-Options` header to
  the response. It prevent resources with invalid media types being
  loaded as stylesheets or scripts.

  This does not have any option.

  More information:
  http://msdn.microsoft.com/en-us/library/ie/gg622941(v=vs.85).aspx
  https://www.owasp.org/index.php/List_of_useful_HTTP_headers"
  [context]
  (hs/set-headers! {"X-Content-Type-Options" "nosniff"})
  (ct/delegate context))

(defn- form-post?
  [context]
  (let [request (:request context)
        method (keyword (.. request getMethod getName toLowerCase))
        content-type (.. request getBody getContentType getType)]
    (and (= :post method)
         (or (= content-type "application/x-www-form-urlencoded")
             (= content-type "multipart/form-data")))))

(defn- csrf-tokens-match?
  [context header-name field-name cookie-name]
  (let [cookies (hs/get-cookies context)
        headers (hs/get-headers context)
        formdata (ps/parse-formdata context)
        htoken (get headers header-name)
        ctoken (get cookies cookie-name)
        ptoken (get formdata field-name)]
    (and (not (nil? ctoken))
         (or (= ctoken ptoken)
             (= ctoken htoken)))))

(defn csrf-protect
  "A chain handler that provides csrf (Cross-site request forgery)
  protection. Also known as a one-click attack or session riding."
  ([] (csrf-protect {}))
  ([{:keys [on-error cookie-name field-name header-name]
     :or {header-name "x-csrftoken"
          field-name "csrftoken"
          cookie-name "csrftoken"}}]
   (fn [context]
     (if (form-post? context)
       (if (csrf-tokens-match? context header-name field-name cookie-name)
         (ct/delegate context)
         (if (fn? on-error)
           (on-error context)
           (http/bad-request "CSRF tokens don't match")))
       (do
         (hs/set-cookies! context {cookie-name {:value (str (uuid/v1))}})
         (ct/delegate context))))))
