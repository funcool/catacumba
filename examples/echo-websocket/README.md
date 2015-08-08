# echo-websocket

A simple example that runs a websocket echo server

## Usage

```
var ws = new WebSocket("ws://localhost:5050");
ws.onmessage = function(msg) { console.log(msg); }
ws.send("foo")
```
