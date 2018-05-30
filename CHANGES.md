# Changelog #

## Version 2.2.1 ##

Date: 2018-05-31

- Update dependencies.

## Version 2.2.0 ##

Date: 2017-04-25

- Update dependencies.
- Add `:allow-credentials` option to CORS handler.


## Version 2.1.0 ##

Date: 2017-03-14

- Update beicon to 3.2.0.
- Update transit to 0.8.300
- Update core.async to 0.3.442
- Update manifold to 0.1.6


## Version 2.0.0 ##

Date: 2017-02-24

- Replce internal reactive-streams adapter for core.async channel
  with beicon (rxjava2) that removes a lot of code and makes it
  more maintenable.
- Update to ratpack 1.4.5 that fixes connection leaking.
  Caused mainly by SSE connections when the client open and closes
  the connection before nothing is received from server.
- Remove builtin support for stuartsierra component.
- Add `delegated-context?` predicate (useful for tests).
- Add `response?` predicated (also useful for tests).
- The `body-params` handler now attaches body to the context if
  it is not already attached to it (this is useful when no appropiate
  parser is found for the incoming content type).
- Update dependencies.
- Make SSE handlers look and work in the same way as websocket
  handlers, improving usability and fixing unnecesary inconsistencies.


## Version 1.2.0 ##

Date: 2016-11-15

- Update `buddy-sign` to 1.3.0
- Update `transit-clj` to 0.8.293
- Update `promesa` to 1.6.0
- Update `cuerdas` to 2.0.1
- Update `ns-tracker` to 0.3.1
- Update `core.async` to 0.2.395
- Update `ratpack` to 1.4.4


## Version 1.1.1 ##

Date: 2016-09-17

- Update ratpack to 1.4.2
- Update netty to 4.1.5.Final


## Version 1.1.0 ##

Date: 2016-09-01

- Update cuerdas to 1.0.1
- Update buddy-sign to 1.2.0


## Version 1.0.2 ##

Date: 2016-08-28

- Add `:host` parameter to server (allowing setting the socket bind host).
  (many thanks to @hamidr)


## Version 1.0.1 ##

Date: 2016-08-23

- Update ratpack to 1.4.1
- Update promesa to 1.5.0


## Version 1.0.0 ##

Date: 2016-08-17

Important changes (mostly breaking):

- Remove useless open abstraction for handler factory.
- Remove DefaultContext type and simplify related functions.
- Remove ring adapter (seems like it not very useful).
- Remove postal handlers.
  After using it some time, seems that it has no advantadges over
  standard REST api and has a lot of disadvantatges. If someone want
  continue using it, the code-base is very small and it can be
  maintained out of main codebase.
- Body reading is now **asynchronous**.
  This change implies removing `:body` key from context and provide
  a special function for asynchronously reading body.
  You will be able to restore the previous behavior adding a new
  `parse/read-body` handler on the start of the routing pipeline
  or server decorators (that just reads the body and puts it inside
  the context).
- `get-formdata` function becomes asynchronous and now returns
  a promise of parsed files.
- Upgrade ratpack version to 1.4.0.
- Upgrade netty version to 4.1.4 Final.
- Add edn `application/edn` support for `body-params` handler.

## Version 0.17.0 ##

Date: 2016-06-05

- Fixed wrong usage of transients on body parsing.
  (thanks to @timgluz)
- Remove some reflection warnings on hot code.
- Add support for use symbols as handlers.
  (namespaced symbols that will resolve to a proper handler var).


## Version 0.16.0 ##

Date: 2016-05-24

- Update buddy-sign to 1.0.0 (maybe breaking change).
- Update ratpack to 1.3.3
- Update promesa to 1.2.0


## Version 0.15.0 ##

Date: 2016-04-26

- Remove riddley exclusion on manifold.
- Update buddy-sign to 0.13.0
- Fix bug in CompletableFuture response handling.
- Simplified impl of auth handler.


## Version 0.14.0 ##

Date: 2016-04-23

- Update ratpack to 1.3.1
- Update netty to 4.1.0.CR7
- Remove unused functions from internal executors ns.


## Version 0.13.0 ##

Date: 2016-04-14

- Allow pass additional options to serializers.
- BREAKING CHANGE: rename the default marker file
  from `.catacumba` to `.catacumba.basedir`.
- Add the ability to specify an user defined file name
  for the marker file.
- Add support for serving static files from classpath.
- Update manifold dependency to 0.1.4.
- Update cheshire dependency to 5.6.1
- Update buddy-sign dependency to 0.12.0
- Remove potemkin dependency.


## Version 0.12.0 ##

Date: 2016-03-20

- Improved headers and methods normalization on cors handler
- Improved cors documentation.
- Fix wrong documentation related to logging.
- Update to ratpack 1.2.0
- Update to netty 4.1.0.Beta8
- Update to promesa 1.1.1



## Version 0.11.2 ##

Date: 2016-03-13

- Documentation improvements.
- Better docstrings for serializers api.
- Better docstrings for postal api.
- Minor internal changes on postal impl.
- Minor internal changes on executor impl.


## Version 0.11.1 ##

Date: 2016-02-06

- Update dependencies: prone, manifold, environ.
- Remove explicit cats dependency.
- Set default clojure version to 1.8.0.

## Version 0.11.0 ##

Date: 2016-01-16

- Add implementation for ratpack promises as response type
  (thanks to @christoph-frick).
- Add the ability to return files (java.nio.file.Path) as response
  or body value and transfer it to the user in the most efficient way.
- Add `resolve-file` function for easy resolve files in the current
  FileSystem binding configuration.
- Add prone (error reporting middleware) plugin.


## Version 0.10.1 ##

Date: 2016-01-11

- Fix non-heap memory leak on websockets impl.


## Version 0.10.0 ##

Date: 2016-01-08

Changes:

- Remove slingshot dependency.
- Update buddy-sign version to 0.9.0.
- Update promesa version to 0.7.0.
- Update cats version to 1.2.1.
- Update potemkin version to 0.4.3.


## Version 0.9.0 ##

Date: 2015-12-10

Important changes:

- The `catacumba.handlers` namespace is no longuer available. It was a some
  kind of aggregator of different sub namespaces. The problem with it has
  includes some sub namespaces and some not. For remove the inconsistences
  it was removed. Now you should include the concrete namespace for use
  a concrete functionallity (**BREAKING CHANGE**).
- The `catacumba.handlers.interceptor`, `catacumba.handlers.cors` and
  `catacumba.handlers.autoreload` has moved into one unique namespace called
  `catacumba.handlers.misc` (**BREAKING CHANGE**).
- Add the decorators concept for attach some "root" handlers on the server
  startup.
- The routing policy for methos is changed from catch all to delegate.
  This is means that if you are declaring `get` and `post` methods to
  to the same path it will works as expected. Before that change you
  have to mandatory use `:by-method` for solving that. The `:by-method`
  routing directive is now deprecated becuase is not longuer necessary.
  This is mostly backward compatible change.
- Replace promissum with promesa, that is now cross-platform promise
  library (before it was only for cljs).


Other changes:

- Add proper support for tls/ssl
- Update ratpack to 1.1.1
- Update netty to 4.1.0.Beta7
- Add postal handlers.
- Add request logging facilities.


## Version 0.8.1 ##

Date: 2015-10-17

- Add support for handlers chaining on `:by-method` routing
  directive.
- Add .travis.yml and test catacumba using clojure 1.7 and 1.8.
- Update promissum to 0.3.2.


## Version 0.8.0 ##

Date: 2015-10-12

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
- Fix wrong connection close handling when it is abruptly
  closed by peer (issue inherited from ratpack).
- Allow websocket extensions to be used.
- Allow setup native ratpack handlers directly in router.
- Update component to 0.3.0.
- Update environ to 1.0.1.
- Update buddy-sign to 0.7.1.


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
