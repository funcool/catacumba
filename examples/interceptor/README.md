# interceptor: example project for show how interceptors works #

This is an example that uses interceptors facilites of catacumba for show
how the application can be instrumented for monitoring the time of execution
of requests among other things.

Let start with:

```bash
lein run
```

And go to http://localhost:5050/

For each page renderd you will see a little message in the console showing the time
used for process the request. Here a simple example of possible output:

```bash
$ lein run
[main] INFO ratpack.server.RatpackServer - Ratpack started for http://localhost:5050
Computation :compute elapsed in: 0.025150461 (sec)
Computation :compute elapsed in: 0.001690894 (sec)
Computation :compute elapsed in: 0.001541675 (sec)
Computation :compute elapsed in: 0.001554894 (sec)
Computation :compute elapsed in: 0.00175033 (sec)
```
