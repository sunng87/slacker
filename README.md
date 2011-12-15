# slacker

**"Superman is a slacker."**

slacker is a simple RPC framework for Clojure based on
[aleph](https://github.com/ztellman/aleph) and
[carbonite](https://github.com/sunng87/carbonite/). I forked carbonite
because slacker requires it to work on clojure 1.2.

slacker is growing.

### RPC vs Remote Eval

Before slacker, the clojure world uses a *remote eval* approach for
remoting ([nREPL](https://github.com/clojure/tools.nrepl),
[portal](https://github.com/flatland/portal)).  Comparing to remote
eval, RPC (especially slacker) has some pros and cons:

#### pros

* slacker uses direct function call, which is much faster than eval
  (about *100x*)
* with slacker, only selected functions are exposed, instead of the
  whole java environment when using eval. So it's much securer and
  generally you don't need a sandbox (like clojail) for slacker.

#### cons

* Eval approach provides full features of clojure, you can use
  high-order functions and lazy arguments. Due to the limitation of
  serialization, slacker has its difficulty to support these features.

## Example

An pair of example server/client can be found under "examples", you
can run the examples by `lein run :server` and `lein run :client` . 

## Usage

### Leiningen

    :dependencies [[info.sunng/slacker "0.2.1"]]

### Getting Started

Slacker will expose all your public functions under a given
namespace. 

``` clojure
(ns slapi)
(defn timestamp []
  (System/currentTimeMillis))

;; ...more functions
```             

To expose `slapi`, use:

``` clojure
(use 'slacker.server)
(start-slacker-server (the-ns 'slapi) 2104)
```

On the client side, define a facade for the remote function:

``` clojure
(use 'slacker.client)
(def sc (slackerc "localhost" 2104))
(defremote sc timestamp)
(timestamp)
```

### Client Connection Pool

Slacker also supports connection pool in client API, which enables
high concurrent communication. 

To create a connection pool, use `slackerc-pool` instead of
`slackerc`.

You can configure the pool with following options:

* `:max-active`
* `:exhausted-action`
* `:max-wait`
* `:min-idle`

For the meaning of each option, check the
[javadoc](http://commons.apache.org/pool/apidocs/org/apache/commons/pool/impl/GenericObjectPool.html)
of commons-pool.

### Options in defremote

You are specify the remote function name when the name is occupied in
current namespace

``` clojure
(defremote sc remote-time
  :remote-name "timestamp")
```

If you add an `:async` flag to `defremote`, then the facade will be
asynchronous which returns a *promise* when you call it. You should
deref it by yourself to get the return value.

``` clojure
(defremote timestamp :async true)
@(timestamp)
```

You can also assign a callback for an async facade.

``` clojure
(defremote timestamp :callback #(println %))
(timestamp)
```

### Serializing custom types

By default, most clojure data types are registered in carbonite. (As
kryo requires you to **register** a class before you can serialize
its instances.) However, you may have additional types to
transport between client and server. To add your own types, you should
register custom serializers on *both server side and client side*. Run
this before you start server or client:

``` clojure
(use '[slacker.serialization])
(register-serializers some-serializers)
```
[Carbonite](https://github.com/revelytix/carbonite "carbonite") has
some detailed docs on how to create your own serializers.

### JSON Serialization

Other than binary format, you can also use JSON for
serialization. JSON is a text based format which is more friendly to
human beings. It may be useful for debugging, or communicating with
external applications.

Configure slacker client to use JSON:

``` clojure
(def sc (slackerc "localhost" 2104 :content-type :json))
```

Turn on the debug option, you will see all the JSON data transported
between client and server:

``` clojure
(use 'slacker.common)
(binding [slacker.common/*debug* true]
  (inc-m 100))
```

shows:

        dbg:: [100]
        dbg:: 700
        700

One thing you should note is the representation of keyword in
JSON. Keywords and strings are both encoded as JSON string in
transport. But while decoding, all map keys will be decoded to
keyword, and all other strings will be decoded to clojure string. This
may lead to inconsistency of your clojure data structure between server and
client. Try to avoid this by carefully design your data structure or
just using carbonite(default and recommended).

## Performance

Some performance tests was executed while I'm developing slacker.

A simple test client is [here](https://gist.github.com/1449860). With
the client, as tested on HP DL360 (dual 6 core X5650, 2.66GHz), a
single client (50 connections, 50 threads) performed 500000
**synchronous** calls in 48862 msecs (TPS is about **10232**).

Some formal performance benchmark is coming soon.

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
