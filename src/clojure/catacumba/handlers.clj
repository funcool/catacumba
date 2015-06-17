(ns catacumba.handlers
  (:require [catacumba.handlers core cors security interceptor session auth parsing autoreload]
            [potemkin.namespaces :refer [import-vars]]))

(import-vars
 [catacumba.handlers.core
  basic-request]
 [catacumba.handlers.autoreload
  autoreloader]
 [catacumba.handlers.cors
  cors]
 [catacumba.handlers.parsing
  body-params]
 [catacumba.handlers.interceptor
  interceptor]
 [catacumba.handlers.session
  session]
 [catacumba.handlers.auth
  auth]
 [catacumba.handlers.security
  csp-headers
  frame-options-headers
  hsts-headers
  content-type-options-headers])
