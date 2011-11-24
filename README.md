# slacker

slacker is a deadly simple RPC framework for Clojure based on [aleph](https://github.com/ztellman/aleph) and [carbonite](https://github.com/sunng87/carbonite/).

slacker is growing.

## Usage

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

On the client side, define a remote function and run it with the
client

``` clojure
(use 'slacker.client)
(defremote timestamp)
(def sc (slackerc "localhost" 2104)
(with-slackerc sc (timestamp))
```

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
