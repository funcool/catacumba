# Changelog #

## Version 0.1.0-alpha2 ##

Date: unreleased

- The context now is implemented as record and allows attach additional information.
- Improved context data forwarding, now the data is attached directy to the context.
- Simplified access to request and response, because now are simple keys in the context type.
- Simplified internal abstraction for get/set headers.
- Simplified access to the route params, now are attached directly to the context under
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


## Version 0.1.0-alpha1 ##

Date: 2015-04-19

- First release
