(ns catacumba.handlers
  (:require [catacumba.handlers core cors security interceptor session]
            [potemkin.namespaces :refer [import-vars]]))

(import-vars
 [catacumba.handlers.core
  basic-request]
 [catacumba.handlers.cors
  cors]
 [catacumba.handlers.interceptor
  interceptor]
 [catacumba.handlers.session
  session
  memory-storage]
 [catacumba.handlers.security
  csp-headers
  frame-options-headers
  hsts-headers
  content-type-options-headers])

