(ns catacumba.handlers
  (:require [catacumba.handlers core cors security]
            [potemkin.namespaces :refer [import-vars]]))

(import-vars
 [catacumba.handlers.core
  basic-request]
 [catacumba.handlers.cors
  cors]
 [catacumba.handlers.security
  csp-headers
  frame-options-headers
  hsts-headers])

