# dime

[![Build Status](https://travis-ci.org/kumarshantanu/dime.svg)](https://travis-ci.org/kumarshantanu/dime)

Dependency Injection Made Easy for Clojure.

Dime implements [dependency injection/inversion](https://en.wikipedia.org/wiki/Dependency_inversion_principle) by
creating partially applied functions in an inexpensive (boiler-plate free), mostly automated manner.


## Usage

Leiningen coordinates: `[dime "0.4.0"]`


### Example

Consider a contrived order posting implementation with a decoupled design as shown below. You are supposed to write
code in a similar fashion (with metadata tags) in your application for automatic injection.


#### Annotated functions

Notice the meta data tags (`:expose`, `:inject`, `:post-inject`) used in the code.

```clojure
;; ---------------- in namespace foo.db ----------------

(ns foo.db
  (:require
    [dime.util :as du]))

(defn ^{:expose :connection-pool
        :post-inject du/post-inject-invoke}  ; execute fn to obtain connection-pool
      make-conn-pool
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  :dummy-pool)

(defn ^{:expose :find-items} db-find-items
  [^:inject connection-pool item-ids]
  {:items item-ids})

(defn db-create-order
  [^:inject connection-pool order-data]
  {:created-order order-data})

;; ---------------- in namespace foo.service ----------------

(defn service-create-order
  [^:inject find-items ^:inject db-create-order user-details order-details]
  (let [item-ids   (find-items (:item-ids order-details))
        order-data {:order-data :dummy}]  ; prepare order-data
    (db-create-order order-data)))

;; ---------------- in namespace foo.web ----------------

(defn ^:expose find-user  ; vars must have at least one inject annotation to participate in dependency discovery
  [session]
  :dummy-user)

(defn web-create-order
  [^:inject find-user ^:inject service-create-order web-request]
  (let [user-details (find-user (:session web-request))
        order-details {:order :dummy-order}
        created-order (service-create-order user-details order-details)]
    {:response :dummy-response}))
```


#### Requiring namespaces

You would need the requires namespaces. See the snippet below:

```clojure
(ns foo.init
  (:require
    [dime.core :as di]
    [dime.var  :as dv]))
```


#### Dependency discovery

Discovering dependency graph is quite straightforward:

```clojure
(def deps (dv/ns-vars->graph ['foo.db 'foo.service 'foo.web]))  ; scan namespaces for injectable vars
```


#### Dependency resolution

Prepare seed data and invoke dependency resolution:

```clojure
(defn resolve-dependencies
  [...]
  (let [seed {:db-host "localhost"
              :db-port 3306
              :username "dbuser"
              :password "s3cr3t"}
        ;; `inject-all` resolves/injects dependencies, returning keys associated with partial functions
        {:keys [web-create-order]} (di/inject-all deps seed)]
    ;; now `web-create-order` needs to be called with only `web-request`
    ...))
```


#### Dependency graph visualization

If you are using Leiningen, you can use the [lein-viz](https://github.com/kumarshantanu/lein-viz) plugin for
visualization of the dependency graph. The snippet below is an example for `[lein-viz 0.3.0]`:

```clojure
;; assuming this is in foo.init namespace
(defn viz-payload
  []
  {:graph-data  (di/attr-map graph :dep-ids)
   :node-labels (di/attr-map graph :impl-id)
   :node-shapes (di/attr-map graph #(when (:post-inj %) :rectangle))})
```

Install the plugin and run the following command to visualize the graph:

```
$ lein viz -s foo.init/viz-payload
```


### How it works

* The `:inject` meta data tag is required for all dependency arguments to be injected.
* The `defn` var names are keywordized to form their injection names unless overridden by the `:expose` tag.
* The `:inject` tags on `defn` arguments are matched against the `:expose` names of the `defn` vars.
* Arguments marked with `:inject` are looked up in seed data first, followed by other dependencies.
* The `:pre-inject` tag is used for custom processing before partial fn is created. By default, no processing is done.
* The `:post-inject` tag is used for custom processing after partial fn is created. By default, no processing is done.


#### Injection options

`dime.core/inject-all` (and `dime.core/inject`) allows an option map argument to customize the runtime behavior.

* Option `:pre-inject-processor` may be useful to `deref` the vars (for faster dispatch) before making a partial fn
* Option `:post-inject-processor` may be useful to catch any exceptions arising from post-injection handlers


## License

Copyright © 2016 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
