(ns catacumba.http
  (:require [catacumba.impl.http :refer [response]]))

(defn continue
  ([body] (response body 100))
  ([body headers] (response body 100 headers)))

(defn ok
  ([body] (response body 200))
  ([body headers] (response body 200 headers)))

(defn created
  ([location] (response "" 201 {:location location}))
  ([location body] (response body 201 {:location location}))
  ([location body headers] (response body 201 (merge headers {:location location}))))

(defn accepted
  ([body] (response body 202))
  ([body headers] (response body 202 headers)))

(defn no-content
  ([] (response "" 204))
  ([headers] (response "" 204 headers)))

(defn moved-permanently
  ([location] (response "" 301 {:location location}))
  ([location body] (response body 301 {:location location}))
  ([location body headers] (response body 301 (merge headers {:location location}))))

(defn found
  ([location] (response "" 302 {:location location}))
  ([location body] (response body 302 {:location location}))
  ([location body headers] (response body 302 (merge headers {:location location}))))

(defn see-other
  ([location] (response "" 303 {:location location}))
  ([location body] (response body 303 {:location location}))
  ([location body headers] (response body 303 (merge headers {:location location}))))

(defn temporary-redirect
  ([location] (response "" 307 {:location location}))
  ([location body] (response body 307 {:location location}))
  ([location body headers] (response body 307 (merge headers {:location location}))))

(defn bad-request
  ([body] (response body 400))
  ([body headers] (response body 400 headers)))

(defn unauthorized
  ([body] (response body 401))
  ([body headers] (response body 401 headers)))

(defn forbidden
  ([body] (response body 403))
  ([body headers] (response body 403 headers)))

(defn not-found
  ([body] (response body 404))
  ([body headers] (response body 404 headers)))

(defn method-not-allowed
  ([body] (response body 405))
  ([body headers] (response body 405 headers)))

(defn not-acceptable
  ([body] (response body 406))
  ([body headers] (response body 406 headers)))

(defn conflict
  ([body] (response body 409))
  ([body headers] (response body 409 headers)))

(defn gone
  ([body] (response body 410))
  ([body headers] (response body 410 headers)))

(defn precondition-failed
  ([body] (response body 412))
  ([body headers] (response body 412 headers)))

(defn unsupported-mediatype
  ([body] (response body 415))
  ([body headers] (response body 415 headers)))

(defn too-many-requests
  ([body] (response body 429))
  ([body headers] (response body 429 headers)))

(defn internal-server-error
  ([body] (response body 500))
  ([body headers] (response body 500 headers)))

(defn not-implemented
  ([body] (response body 501))
  ([body headers] (response body 501 headers)))
