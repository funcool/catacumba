;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are met:
;;
;; * Redistributions of source code must retain the above copyright notice, this
;;   list of conditions and the following disclaimer.
;;
;; * Redistributions in binary form must reproduce the above copyright notice,
;;   this list of conditions and the following disclaimer in the documentation
;;   and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
;; AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
;; IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;; DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
;; FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
;; DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
;; SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;; CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
;; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns catacumba.core
  (:require [catacumba.impl server routing context handlers websocket sse]
            [catacumba.impl.helpers :refer(defalias)]))

(defalias run-server catacumba.impl.server/run-server)
(defalias routes catacumba.impl.routing/routes)
(defalias on-close catacumba.impl.context/on-close)
(defalias before-send catacumba.impl.context/before-send)
(defalias delegate catacumba.impl.context/delegate)
(defalias public-address catacumba.impl.context/public-address)
(defalias get-body! catacumba.impl.context/get-body!)
(defalias get-headers catacumba.impl.context/get-headers)
(defalias set-headers! catacumba.impl.context/set-headers!)
(defalias get-cookies catacumba.impl.context/get-cookies)
(defalias set-cookies! catacumba.impl.context/set-cookies!)
(defalias set-status! catacumba.impl.context/set-status!)
(defalias get-formdata catacumba.impl.context/get-formdata)
(defalias get-query-params catacumba.impl.context/get-query-params)
(defalias send! catacumba.impl.handlers/send!)
(defalias websocket catacumba.impl.websocket/websocket)
(defalias sse catacumba.impl.sse/sse)
