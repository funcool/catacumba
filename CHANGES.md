# Changelog #

## Version 0.1.0-alpha2 ##

Date: unreleased

- The context now is implemented as record and allows attach additional information.
- Improved context data forwarding, now the data is attached directy to the context.
- Simplified access to request and response, because now are simple keys in the context type.
- Simplified internal abstraction for get/set headers.
- Simplified access to the route params, now are attached directly to the context under
  the `:route-params` key.
- Update to clojure 1.7 beta 2.
- Add support for Server-Sent Events.
- Add CORS support with chain handler.
- Add "context as request" helper chain handler.
- Add security related chain helpers (x-frame-options, strict-transport-security
  and the content-security-policy).


## Version 0.1.0-alpha1 ##

Date: 2015-04-19

- First release
