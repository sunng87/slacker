# slacker

**"Superman is a slacker."**

slacker is a simple RPC framework for Clojure based on
[aleph](https://github.com/ztellman/aleph) and
[carbonite](https://github.com/sunng87/carbonite/). I forked carbonite
because slacker requires it to work on clojure 1.2.

slacker is growing.

### RPC vs Remote Eval

Before slacker, the clojure world uses a *remote eval* approach for
remoting. Comparing to remote eval, RPC (especially slacker) has some
pros and cons:

#### pros

* slacker uses direct function call, which is much faster than eval
  (about *100x*)
* with slacker, you can select a set of functions to expose, instead
  of the whole system in eval. So it's much securer and generally you
  don't need a sandbox (like clojail) for slacker.

#### cons

* Eval approach provides full features of clojure, you can use
  high-order functions and lazy arguments. Due to the limitation of
  serialization, slacker has its difficulty to support these features.

## Usage

### Leiningen

    :dependencies [[info.sunng/slacker "0.1.0"]]

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
(def sc (slackerc "localhost" 2104)
(defremote sc timestamp)
(timestamp)
```

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
(use '[slacker common])
(register-serializers some-serializers)
```
[Carbonite](https://github.com/revelytix/carbonite "carbonite") has
some detailed docs on how to create your own serializers.

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
