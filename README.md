# slacker

slacker is a deadly simple RPC framework for Clojure based on [aleph](https://github.com/ztellman/aleph) and [carbonite](https://github.com/sunng87/carbonite/).

slacker is growing.

## Usage

### Leiningen

    :dependencies [[info.sunng/slacker "0.1.0-SNAPSHOT"]]

### API

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

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
