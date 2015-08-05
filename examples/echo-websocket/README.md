# echo-websocket

A simple example that runs a websocket echo server

## Usage

```
var ws = new WebSocket("ws://localhost:5050");
ws.onmessage = function(msg) { console.log(msg); }
ws.send("foo")
```

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
