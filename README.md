# slacker

![slacker](http://i.imgur.com/Jd02f.png)

slacker is a simple RPC framework designed for Clojure and created by
Clojure.

[![Build Status](https://travis-ci.org/sunng87/slacker.png?branch=master)](https://travis-ci.org/sunng87/slacker)
[![Dependency Status](https://www.versioneye.com/user/projects/5358b3e8fe0d07af35000052/badge.png)](https://www.versioneye.com/user/projects/5358b3e8fe0d07af35000052)

## Features

* Fast network infrastructure
* Fast serialization based on Kryo or Nippy (Text based serialization is also supported)
* Transparent and non-invasive API. Remote calls is just like local
  calls with slacker.
* Extensible server with interceptor framework.
* Flexible cluster with Zookeeper (moved to [slacker-cluster](https://github.com/sunng87/slacker-cluster))

## Examples

A pair of example server/client can be found under "examples", you
can run the examples by `lein run-example-server` and
`lein run-example-client`. The example client will print out various
outputs from the server as well as a RuntimeException: "Expected exception."
This exception is part of the example - not a genuine error in the slacker
source code.

## Usage

### Leiningen


![latest version on clojars](https://clojars.org/slacker/slacker/latest-version.svg)

### Basic Usage

Slacker will expose all your public functions under a given namespace.

``` clojure
(ns slapi)
(defn timestamp
  "return server time in milliseconds"
  []
  (System/currentTimeMillis))

;; ...more functions
```

To expose `slapi` from port 2104, use:

``` clojure
(use 'slacker.server)
(start-slacker-server [(the-ns 'slapi)] 2104)
```

Multiple namespaces can be exposed by appending them to the vector

You can also add option `:threads 50` to configure the size of server
thread pool.

On the client side, You can use `defn-remote` to create facade one by
one. Remember to add remote namespace here as facade name,
`slapi/timestamp`, eg. Otherwise, the name of current namespace will
be treated as remote namespace.

``` clojure
(use 'slacker.client)
(def sc (slackerc "localhost:2104"))
(defn-remote sc slapi/timestamp)
(timestamp)
```

Also the `use-remote` function is convenience for importing all functions
under a remote namespace. (Note that `use-remote` uses inspection
calls to fetch remote functions, so network is required.)

``` clojure
(use-remote 'sc 'slapi)
(timestamp)
```

By checking the metadata of `timestamp`, you can get some useful
information:

``` clojure
(slacker-meta timestamp)
=> {:slacker-remote-name "timestamp", :slacker-remote-fn true,
:slacker-client #<SlackerClient
slacker.client.common.SlackerClient@575752>, :slacker-remote-ns
"slapi" :arglists ([]), :name timestamp
:doc "return server time in milliseconds"}
```

### Advanced Usage

#### Options in defn-remote

You can specify the remote function name when there are conflicts in
current namespace.

``` clojure
(defn-remote sc remote-time
  :remote-ns "slapi"
  :remote-name "timestamp")
```

If you add an `:async?` flag to `defn-remote`, then the facade will be
asynchronous which returns a *promise* when you call it. You should
deref it by yourself to get the return value.

``` clojure
(defn-remote sc slapi/timestamp :async? true)
@(timestamp)
```

You can also assign a callback `(fn [error result])` for an
asynchronous facade.

``` clojure
(defn-remote sc slapi/timestamp :callback #(println %2))
(timestamp)
```

The callback accepts to arguments

* error
* result

You need to check (nil? error) because reading the result. Also note
that doing blocking tasks in callback function could ruin system
performance.

#### Serialiation

As of 0.12, slacker is still using
[carbonite (my fork)](https://github.com/sunng87/carbonite) by default
to generate a binary format of data for exchange. I will switch to
Peter Taoussanis's [nippy](https://github.com/ptaoussanis/nippy) in
next release.

##### Serializing custom types

**(deprecated since 0.12)** By default, most clojure data types are
registered in carbonite. (As kryo requires you to **register** a class
before you can serialize its instances.) However, you may have
additional types to transport between client and server. To add your
own types, you should register custom serializers on *both server side
and client side*. Run this before you start server or client:

``` clojure
(use '[slacker.serialization])
(register-serializers {Class Serializer)
```
[Carbonite](https://github.com/revelytix/carbonite "carbonite") has
some detailed docs on how to create your own serializers.

##### JSON Serialization

Other than binary format, you can also use JSON for
serialization. JSON is a text based format which is more friendly to
human beings. It may be useful for debugging, or communicating with
external applications.

Configure slacker client to use JSON:

``` clojure
(def sc (slackerc "localhost:2104" :content-type :json))
```

One thing you should note is the representation of keyword in
JSON. Keywords and strings are both encoded as JSON string in
transport. But while decoding, all map keys will be decoded to
keyword, and all other strings will be decoded to clojure string. This
may lead to inconsistency of your clojure data structure between server and
client. Try to avoid this by carefully design your data structure or
just using carbonite(default and recommended).

##### EDN Serialization

From slacker 0.4, clojure pr/read is supported. You can just
set content-type as `:clj`. clojure pr/read has full support on
clojure data structures and also easy for debugging. However, it's
much slower that carbonite so you'd better not use it if you have
critical performance requirements.

##### Nippy Serialization

Slacker 0.12 has a preview of
[nippy](https://github.com/ptaoussanis/nippy) serialization. Set the
content-type as `:nippy` to use it. Nippy has excellent support for
custom types, you can find detailed information on its page.

Slacker is using nippy custom type code `92` for serializing
stacktrace elements in debug mode. You'd better to avoid this code in
your project. [I was talking with Peter about a namespaced custom type
coded to avoid this conflict](https://github.com/ptaoussanis/nippy/issues/50).

#### Server interceptors

To add custom functions on server, you can define custom
interceptors before or after function called.

``` clojure
(definterceptor logging-interceptor
   :before (fn [req] (println (str "calling: " (:fname req))) req))

(start-slacker-server (the-ns 'slapi) 2104
                      :interceptors (interceptors logging-interceptor))
```

For more information about using interceptors and creating your own
interceptors, query the [wiki
page](https://github.com/sunng87/slacker/wiki/Interceptors).

#### Slacker on HTTP

From 0.4, slacker can be configured to run on HTTP protocol. To
enable HTTP transport, just add a `:http` option to your slacker
server:

``` clojure
(start-slacker-server ...
                      :http 4104)
```

The HTTP url pattern is
http://localhost:4104/*namespace*/*function-name*.*format*.  Arguments
are encoded in *format*, and posted to server via HTTP body. If you
have multiple arguments, you should put them into a clojure vector
(for clj format) or javascript array (for json format).

See a curl example:

``` bash
$ curl -d "[5]" http://localhost:4104/slapi/rand-ints.clj
(38945142 1413770549 1361247669 1899499977 1281637740)
```

Note that you can only use `json` or `clj` as format. Because HTTP is
a text based protocol, so `carb` won't be supported.

#### Slacker as a Ring App

You can also use slacker as a ring app with
`slacker.server/slacker-ring-app`. The ring app is fully compatible
with ring spec. So it could be deployed on any ring adapter.

``` clojure
(use 'slacker.server)
(use 'ring.adapter.jetty)

(run-jetty (slacker-ring-app (the-ns 'slapi))  {:port 8080})
```

The url pattern of this ring app is same as slacker's built-in http
module.

#### Access Control List

Slacker 0.7 supports IP based access control list (ACL). Consult [wiki
page](https://github.com/sunng87/slacker/wiki/AccessControlList) for
the ACL rule DSL.

#### Custom client on function call

One issue with previous version of slacker is you have to define a
remote function with a slacker client, then call this function with
that client always. This is inflexible.

From 0.10.3, we added a macro `with-slackerc` to isolate local
function facade and a specific client. You can call the function with
any slacker client.

```clojure
;; conn0 and conn1 are two slacker clients

(defn-remote conn0 timestamp)

;; call the function with conn0
(timestamp)

;; call the function with conn1
(with-slackerc conn1
  (timestamp))
```

Note that you have ensure that the function you call is also available
to the client. Otherwise, there will be a `not-found` exception
raised.

## Performance

To test performance, just start an example server with `lein run -m
slacker.example.server`.

Then run the performance test script:
`lein exec -p scripts/performance-test.clj 200000 50`. This will run
200,000 calls with 50 threads.

Tested on my working desktop (DELL optiplex 760, Intel(R) Core(TM)2
Duo CPU E7400 @ 2.80GHz, 8G memory), without any special JVM optimization.
**200,000** calls with **50** threads is completed in **21923.806054
msecs**, which means slacker could handle more than **9000** calls per
second on this machine.

## Change logs

See [wiki page](https://github.com/sunng87/slacker/wiki/VersionHistory)

## Contributors

* [lbt05](https://github.com/lbt05)
* [johnchapin](https://github.com/johnchapin)

## License

Copyright (C) 2011-2014 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
