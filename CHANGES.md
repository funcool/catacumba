# Changelog #

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
