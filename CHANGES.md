# Changelog #

## Version 0.8.0 ##

Date: Unreleased

- Session handler and storage abstraction refactored
  to be more flexible and support cookie signed like
  session storages.
- Add csrf documentation.
- Improve csrf security handler implementation.
- Start using ratpack's chain `.path` method instread of
  `.prefix` for attach handlers for `:any` and `:all` routing
  directives. (This a more correct way to handle it and it
  should not cause regressions).
- Add restful handlers. This will allow build and expose
  restful resources in a simpler way.


## Version 0.7.1 ##

Date: 2015-09-19

- Upgrade buddy-sign to 0.7.0


## Version 0.7.0 ##

Date: 2015-09-19

- Upgrade to ratpack 1.0.0
- Upgrade to promissum 0.3.0
- Upgrade to cats 1.0.0
- Improve the auth handlers and its support for multiple backends.
- Add transit encoding support for body-parsing handler.


## Version 0.6.2 ##

Date: 2015-08-31

- Properly collect all route params when using multiple route nesting.
  (Thanks to @prepor for report and patch)


## Version 0.6.1 ##

Date: 2015-08-24

- Fix wrong handling of route parameters together with context cache strategy.


## Version 0.6.0 ##

Date: 2015-08-22

- Improved inconsistences on response handling.
  Now the manifold stream and the reactive-stream published
  are not allowed as response value bacause them represents
  a collection of values and this consept does not fits well
  with response that is one unique value. They continue to be
  supported as body value. This is a BREAKING CHANGE.
- Reimplement the context management.
  Now is behaves like any other response value instead of special
  case and does not requires contex as first parameter. This change
  allows remove the special case for CPS style handler and also allow
  the fully asynchronous handlers chain.
  This is a BREAKING CHANGE because the signature of the `delegate`
  function is changed: it longer accepts context as parameter and
  now it should be a return value of the handler in sync or async
  way.
- The keys of the headers, query-params and form params maps are
  now keywordized. BREAKING CHANGE.
- Performance improvements on context building across handlers
  chain execution process.
- The slf4j-simple is no longer included by default. So if you
  want the previous behavior, just include `[org.slf4j/slf4j-simple "1.7.12"]`
  on your dependency list.


## Version 0.5.0 ##

Date: 2015-08-11

- Removed `get-body` function.
- The context is rich by default with keyword access to all most used
  request attributes such as body, http method, headers, cookies, params, etc...
- Websocket internals are rewritten (now they uses completable futures for
  backpressure control).
- The `parse-queryparams` is replaced by `get-query-params`, and is should
  not be used directly by the user because the parsed query params are directly
  available in context through the `:query-params` entry.
- Update to ratpack 0.9.19 and prepare code to up coming ratpack 1.0.0 (that
  implies a lot of changes in the catacumba internals and performance
  improvements).
- Add classpath basedir resolve method (thanks to @mitchelkuijpers).
- Make more easy the example project maintenance.
- Add websocket-echo example (thanks to @mitchelkuijpers).


## Version 0.4.0 ##

Date: 2015-07-19

- Make `:assets` routing directive to be more flexible (breaking change).
- Change the signature of the `:by-method` routing directive (breaking change).
- Fix wrong return value on session auth backend.
- Use `promissum` library instead of `futura`.
- Import the `streams` implementation from `futura` library (`futura` library
  is no longer used by _catacumba_).
- Remove `buddy-auth` dependency that is now completelly useless.
- Update to ratpack 0.9.18
- Update buddy dependency to 0.6.0
- Update potemkin to 0.4.1


## Version 0.3.2 ##

Date: 2015-07-01

- Set default clojure version to 1.7


## Version 0.3.1 ##

Date: 2015-06-28

- Fix wrong dependency scope for ns-tracker dependency.
- Fix wrong exclusion of clojure.core var on helpers namespace.


## Version 0.3.0 ##

Date: 2015-06-23

- Update to ratpack 0.9.17
- Update to netty 4.1.0 beta5
- Update to clojure 1.7.0 rc2
- Update to futura 0.3.0
- Add csrf protect support (via chain handler)
- Add autoreload support.
- Add builtin testing facilities.
- Add cps style handlers (not documented at this moment).
- Reimplement the auth api for to be async.


## Version 0.2.0 ##

Date: 2015-05-30

- Improve session storage api and make it asynchronous by default.
- Add the ability to force the public address value on server startup (`:public-address` option)
- Add an option for specify the custom maximum body size (`:max-body-size` option)
- Add helper for parse the query params: `catacumba.core/parse-queryparams`.
- Add support for interceptors (instrumentation)
- Add support for auth facilities.
- Add support for extensible body parsing.
- Update futura library to the last version that adds fully asynchronous publishers.
- Remove support for automatic searching of `catacumba.properties` file for basedir.


## Version 0.1.0-alpha2 ##

Date: 2015-05-03

- The context now is implemented as record and allows attach additional information.
- Improve context data forwarding, now the data is attached directy to the context.
- Simplify access to request and response, because now are simple keys in the context type.
- Simplify internal abstraction for get/set headers.
- Simplify access to the route params, now are attached directly to the context under
  the `:route-params` key.
- Add support for Server-Sent Events.
- Add CORS support with as chain handler.
- Add "context as request" helper chain handler.
- Add security related chain helpers (x-frame-options, strict-transport-security,
  the content-security-policy and x-content-type-options).
- Add support for manifold deferreds as body and response.
- Add support for manifold streams as body and response.
- Add support for get/set cookies.
- Add basic support for sessions (very experimental and internal api will change
  in the next version).
- Add special functions for add callback function that will be executed when
  connection is closed and just before send the response to client.
- Add dispatch by method to the routing system.
- Update to clojure 1.7 beta 2.
- Update futura library to 0.1.0-alpha2
- Update ratpack to 0.9.16.
- Add support for stuartsierra/component.


## Version 0.1.0-alpha1 ##

Date: 2015-04-19

- First release
