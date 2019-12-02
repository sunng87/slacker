# Slacker User Guide

## TOC

* Starting slacker server
* Starting slacker clients
* Remote function declaration
* Remote namespace declaration
* TLS
* Simple HTTP server
* More serialization options
* Async remote function
* Callback style remote function
* Server interceptors
* Client interceptors
* Timeout
* Deadline
* Server threadpool and queue
  * Per-namespace threadpool
* Interruption on timeout
* Graceful shutdown
* Getting remote function metadata
* Tracing and extension
* Max in-flight requests
* Client Keep-alive
* Stopping slacker server

## Starting slacker server

To expose your clojure namespaces via slacker, you will need to start
a slacker server to serve these namespaces. Slacker server can be
started via `slacker.server/start-slacker-server`.

A port and a collection of functions are required for
`start-slacker-server`. The service will be exposed on the port and
bound to `0.0.0.0` by default.

For function collection, there are three types:

* Exposing all public functions under a namespace, for instance,
  `(the-ns 'slacker.example.api)`.
* A map of namespace and functions, `{"slacker.example.api2" {"echo"
  (fn [data] data)}}`.
* A vector of any function collections of above two.

You may also provide a thread pool for running these functions, we
will discuss these options in [Server threadpool and
queue](#Server_threadpool_and_queue)

## Starting slacker clients
