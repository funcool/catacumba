(ns catacumba.handlers.security
  (:require [catacumba.impl.context :as ct]
            [catacumba.impl.handlers :as hs]
            [cuerdas.core :as str])
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
