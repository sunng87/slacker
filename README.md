# slacker

**"Superman is a slacker."**

slacker is a simple RPC framework for Clojure based on
[aleph](https://github.com/ztellman/aleph) and
[carbonite](https://github.com/sunng87/carbonite/). I forked carbonite
because slacker requires it to work on clojure 1.2.

slacker is growing.

### Slacker VS. Remote Eval

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

### Slacker VS. Thrift, Protobuf

Since clojure is also hosted on Java virtual machine, you can use
existed Java RPC framework in clojure, like protobuf and thrift. 

#### pros

* No schema needed
* No additional code generation
* Transparent, non-invasive API
* Pure clojure way
* Highly customizable (interceptor framework)

#### cons

* Currently, there is no cross language support. Slacker is only for
  clojure. I am planning to add nodejs support via clojurescript. 
* Potential performance improvements.

## Example

An pair of example server/client can be found under "examples", you
can run the examples by `lein run :server` and `lein run :client` . 

## Usage

### Leiningen

    :dependencies [[slacker "0.5.0-SNAPSHOT"]]

### Getting Started

Slacker will expose all your public functions under a given
namespace. 

``` clojure
(ns slapi)
(defn timestamp 
  "return server time in milliseconds"
  []
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
(use-remote 'sc 'slapi)
(timestamp)
```

By checking the metadata of `timestamp`, you can find useful
information:

``` clojure
(meta timestamp)
=> {:slacker-remote-name "timestamp", :slacker-remote-fn true,
:slacker-client #<SlackerClient
slacker.client.common.SlackerClient@575752>, :slacker-remote-ns
"slapi" :arglists ([]), :name timestamp 
:doc "return server time in milliseconds"}
```

#### Closing the client

``` clojure
(close-slackerc sc)
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

### Options in defn-remote

You are specify the remote function name when the name is occupied in
current namespace

``` clojure
(defn-remote sc remote-time
  :remote-ns "slapi"
  :remote-name "timestamp")
```

If you add an `:async` flag to `defn-remote`, then the facade will be
asynchronous which returns a *promise* when you call it. You should
deref it by yourself to get the return value.

``` clojure
(defn-remote timestamp :async true :remote-ns "slapi")
@(timestamp)
```

You can also assign a callback for an async facade.

``` clojure
(defn-remote timestamp :remote-ns "slapi" :callback #(println %))
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

From slacker 0.4.0, clojure pr/read is supported. You can just
set content-type as `:clj`. clojure pr/read has full support on
clojure data structures and also easy for debugging. However, it's
much slower that carbonite so you'd better not use it if you have
critical performance requirements.

### Server interceptors

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

### Slacker on HTTP

From 0.4.0, slacker can be configured to run on HTTP protocol. To
enable HTTP transport, just add a `:http` option to your slacker
server:

``` clojure
(start-slacker-server ...
                      :http 4104)
```

The HTTP url pattern is
http://localhost:4104/*function-name*.*format*. Arguments are encoded
in *format*, and posted to server via HTTP body. If you have multiple
arguments, you should put them into a clojure vector (for clj format)
or javascript array (for json format).

See a curl example:

``` bash
$ curl -d "[5]" http://localhost:4104/rand-ints.clj
(38945142 1413770549 1361247669 1899499977 1281637740)
```

Note that you can only use `json` or `clj` as format. Because HTTP is
a test based protocol, so `carb` will not be supported.

## Slacker Cluster

Leveraging on ZooKeeper, slacker now has a solution for high
availability and load balancing. You can have several slacker servers
in a cluster serving functions. And the clustered slacker client will
randomly select one of these server to invoke. Once a server is added
to or removed from the cluster, the client will automatically
establish connection to it. 

To create such a slacker cluster, make sure you have a zookeeper
instance in your network.

### Clustered Slacker Server

On the server side, add an option `:cluster`. Some information is    
required:

``` clojure
(use 'slacker.server)
(start-slacker-server ...
                      :cluster {:name "cluster-name"
                                :zk "127.0.0.1:2181"})
```

Cluster information here:

* `:name` the cluster name (*required*)
* `:zk` zookeeper server address (*required*)
* `:node` server IP (*optional*, if you don't provide the server IP
  here, slacker will try to detect server IP by connecting to zk,
  on which we assume that your slacker server are bound on the same
  network with zookeeper)

### Clustered Slacker Client

On the client side, you have to specify the zookeeper address instead
of a particular slacker server. Use the `clustered-slackerc`:

``` clojure
(use 'slacker.client.cluster)
(def sc (clustered-slackerc "cluster-name" "127.0.0.1:2181"))
(defn-remote sc timestamp)
```

You should make sure to use the `defn-remote` from
`slacker.client.cluster` instead of the one from `slacker.client`.

### Examples

There is a cluster example in the source code. To run the server,
start a zookeeper on your machine (127.0.0.1:2181)

Start server instance:

    lein run -m slacker.example.cluster-server 2104

Open a new terminal, start another server instance:

    lein run -m slacker.example.cluster-server 2105

On another terminal, you can run the example client:

    lein run -m slacker.example.cluster-client

By checking logs, you can trace the calls on each server instance.

## Performance

Some performance tests was executed while I'm developing slacker.

A simple test client is [here](https://gist.github.com/1449860). With
the client, as tested on HP DL360 (dual 6 core X5650, 2.66GHz), a
single client (50 connections, 50 threads) performed 500000
**synchronous** calls in 48862 msecs (TPS is about **10232**).

Some formal performance benchmark is coming soon.

## Contributors

* [lbt05](https://github.com/lbt05)

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
