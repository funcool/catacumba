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

(ns catacumba.http
  (:require [catacumba.impl.http :as http]))

(defn response
  "Create a response instance."
  ([body] (http/response body 200 {}))
  ([body status] (http/response body status {}))
  ([body status headers] (http/response body status headers)))

(defn continue
  ([body] (http/response body 100))
  ([body headers] (http/response body 100 headers)))

(defn ok
  "HTTP 200 OK
  Should be used to indicate nonspecific success. Must not be used to
  communicate errors in the response body.

  In most cases, 200 is the code the client hopes to see. It indicates that
  the REST API successfully carried out whatever action the client requested,
  and that no more specific code in the 2xx series is appropriate. Unlike
  the 204 status code, a 200 response should include a response body."
  ([body] (http/response body 200))
  ([body headers] (http/response body 200 headers)))

(defn created
  "HTTP 201 Created
  Must be used to indicate successful resource creation.

  A REST API responds with the 201 status code whenever a collection creates,
  or a store adds, a new resource at the client's request. There may also be
  times when a new resource is created as a result of some controller action,
  in which case 201 would also be an appropriate response."
  ([location] (http/response "" 201 {:location location}))
  ([location body] (http/response body 201 {:location location}))
  ([location body headers] (http/response body 201 (merge headers {:location location}))))

(defn accepted
  "HTTP 202 Accepted
  Must be used to indicate successful start of an asynchronous action.

  A 202 response indicates that the client's request will be handled
  asynchronously. This response status code tells the client that the request
  appears valid, but it still may have problems once it's finally processed.
  A 202 response is typically used for actions that take a long while to
  process."
  ([body] (http/response body 202))
  ([body headers] (http/response body 202 headers)))

(defn no-content
  "HTTP 204 No Content
  Should be used when the response body is intentionally empty.

  The 204 status code is usually sent out in response to a PUT, POST, or
  DELETE request, when the REST API declines to send back any status message
  or representation in the response message's body. An API may also send 204
  in conjunction with a GET request to indicate that the requested resource
  exists, but has no state representation to include in the body."
  ([] (http/response "" 204))
  ([headers] (http/response "" 204 headers)))

(defn moved-permanently
  "301 Moved Permanently
  Should be used to relocate resources.

  The 301 status code indicates that the REST API's resource model has been
  significantly redesigned and a new permanent URI has been assigned to the
  client's requested resource. The REST API should specify the new URI in
  the response's Location header."
  ([location] (http/response "" 301 {:location location}))
  ([location body] (http/response body 301 {:location location}))
  ([location body headers] (http/response body 301 (merge headers {:location location}))))

(defn found
  "HTTP 302 Found
  Should not be used.

  The intended semantics of the 302 response code have been misunderstood
  by programmers and incorrectly implemented in programs since version 1.0
  of the HTTP protocol.
  The confusion centers on whether it is appropriate for a client to always
  automatically issue a follow-up GET request to the URI in response's
  Location header, regardless of the original request's method. For the
  record, the intent of 302 is that this automatic redirect behavior only
  applies if the client's original request used either the GET or HEAD
  method.

  To clear things up, HTTP 1.1 introduced status codes 303 (\"See Other\")
  and 307 (\"Temporary Redirect\"), either of which should be used
  instead of 302."
  ([location] (http/response "" 302 {:location location}))
  ([location body] (http/response body 302 {:location location}))
  ([location body headers] (http/response body 302 (merge headers {:location location}))))

(defn see-other
  "HTTP 303 See Other
  Should be used to refer the client to a different URI.

  A 303 response indicates that a controller resource has finished its work,
  but instead of sending a potentially unwanted response body, it sends the
  client the URI of a response resource. This can be the URI of a temporary
  status message, or the URI to some already existing, more permanent,
  resource.
  Generally speaking, the 303 status code allows a REST API to send a
  reference to a resource without forcing the client to download its state.
  Instead, the client may send a GET request to the value of the Location
  header."
  ([location] (http/response "" 303 {:location location}))
  ([location body] (http/response body 303 {:location location}))
  ([location body headers] (http/response body 303 (merge headers {:location location}))))

(defn temporary-redirect
  "HTTP 307 Temporary Redirect
  Should be used to tell clients to resubmit the request to another URI.

  HTTP/1.1 introduced the 307 status code to reiterate the originally
  intended semantics of the 302 (\"Found\") status code. A 307 response
  indicates that the REST API is not going to process the client's request.
  Instead, the client should resubmit the request to the URI specified by
  the response message's Location header.

  A REST API can use this status code to assign a temporary URI to the
  client's requested resource. For example, a 307 response can be used to
  shift a client request over to another host."
  ([location] (http/response "" 307 {:location location}))
  ([location body] (http/response body 307 {:location location}))
  ([location body headers] (http/response body 307 (merge headers {:location location}))))

(defn bad-request
  "HTTP 400 Bad Request
  May be used to indicate nonspecific failure.

  400 is the generic client-side error status, used when no other 4xx error
  code is appropriate."
  ([body] (http/response body 400))
  ([body headers] (http/response body 400 headers)))

(defn unauthorized
  "HTTP 401 Unauthorized
  Must be used when there is a problem with the client credentials.

  A 401 error response indicates that the client tried to operate on a
  protected resource without providing the proper authorization. It may have
  provided the wrong credentials or none at all."
  ([body] (http/response body 401))
  ([body headers] (http/response body 401 headers)))

(defn forbidden
  "HTTP 403 Forbidden
  Should be used to forbid access regardless of authorization state.

  A 403 error response indicates that the client's request is formed
  correctly, but the REST API refuses to honor it. A 403 response is not a
  case of insufficient client credentials; that would be 401 (\"Unauthorized\").
  REST APIs use 403 to enforce application-level permissions. For example, a
  client may be authorized to interact with some, but not all of a REST API's
  resources. If the client attempts a resource interaction that is outside of
  its permitted scope, the REST API should respond with 403."
  ([body] (http/response body 403))
  ([body headers] (http/response body 403 headers)))

(defn not-found
  "HTTP 404 Not Found
  Must be used when a client's URI cannot be mapped to a resource.

  The 404 error status code indicates that the REST API can't map the
  client's URI to a resource."
  ([body] (http/response body 404))
  ([body headers] (http/response body 404 headers)))

(defn method-not-allowed
  ([body] (http/response body 405))
  ([body headers] (http/response body 405 headers)))

(defn not-acceptable
  ([body] (http/response body 406))
  ([body headers] (http/response body 406 headers)))

(defn conflict
  ([body] (http/response body 409))
  ([body headers] (http/response body 409 headers)))

(defn gone
  ([body] (http/response body 410))
  ([body headers] (http/response body 410 headers)))

(defn precondition-failed
  ([body] (http/response body 412))
  ([body headers] (http/response body 412 headers)))

(defn unsupported-mediatype
  ([body] (http/response body 415))
  ([body headers] (http/response body 415 headers)))

(defn too-many-requests
  ([body] (http/response body 429))
  ([body headers] (http/response body 429 headers)))

(defn internal-server-error
  ([body] (http/response body 500))
  ([body headers] (http/response body 500 headers)))

(defn not-implemented
  ([body] (http/response body 501))
  ([body headers] (http/response body 501 headers)))
