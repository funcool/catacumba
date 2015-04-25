(ns catacumba.handlers
  (:require [catacumba.handlers core cors]
            [potemkin.namespaces :refer [import-vars]]))

(import-vars
 [catacumba.handlers.core
  basic-request]
 [catacumba.handlers.cors
  cors])

