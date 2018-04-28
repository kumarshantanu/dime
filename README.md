# dime

[![Build Status](https://travis-ci.org/kumarshantanu/dime.svg)](https://travis-ci.org/kumarshantanu/dime)

Dependency Injection Made Easy for Clojure.


## Rationale

Initializing and wiring up components in a non-trivial application could be a complex and brittle affair.
It could be stateful, repetitive, messy or all of those. Dime aims to make that process less error-prone,
repeatable and easy to reason about by implementing mostly-automated
[dependency injection/inversion](https://en.wikipedia.org/wiki/Dependency_inversion_principle).


### Goals

- Easy, push-model dependency injection in the large
- Flexible and easy lifecycle management
- Thorough testability, avoiding [pitfalls](http://charsequence.blogspot.in/2016/12/mocking-with-var-redefinition.html)
- Avoid mutation (except in development)


### Caveats (Trade off)

- Cascading dependencies
- Overhead of tagging all dependencies
- Not compatible with multimethods out of the box


### Other work

The following projects take different approaches to depenency management:

- [Component](https://github.com/stuartsierra/component)
- [Mount](https://github.com/tolitius/mount)
- [Mount-lite](https://github.com/aroemers/mount-lite)
- [Integrant](https://github.com/weavejester/integrant)


## Usage

Leiningen coordinates: `[dime "0.5.0-alpha1"]`


### Example

Consider a contrived order posting implementation with a decoupled design as shown below. The example code
below declares the dependencies across functions (with metadata tags) for automatic dependency injection.


#### Annotated functions

Notice the meta data tags (`:expose`, `:inject`, `:post-inject`) used in the code.

```clojure
;; ---------------- in namespace foo.db ----------------

(ns foo.db
  (:require
    [dime.util :as du]))

(defn ^{:expose :connection-pool             ; expose as :connection-pool in dependency graph
        :post-inject du/post-inject-invoke}  ; execute fn to obtain connection-pool
      make-conn-pool
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  :dummy-pool)

(defn ^{:expose :find-items} db-find-items   ; expose as :find-items in dependency graph
  [^:inject connection-pool item-ids]        ; lookup/inject :connection-pool from dependency graph
  {:items item-ids})

(defn db-create-order                        ; expose as :db-create-order in dependency graph
  [^:inject connection-pool order-data]      ; lookup/inject :connection-pool from dependency graph
  {:created-order order-data})

;; ---------------- in namespace foo.service ----------------

(defn service-create-order
  [^:inject find-items ^:inject db-create-order user-details order-details]
  (let [item-ids   (find-items (:item-ids order-details))
        order-data {:order-data :dummy}]  ; prepare order-data
    (db-create-order order-data)))

;; ---------------- in namespace foo.web ----------------

(defn ^:expose find-user  ; must have at least one DIME annotation for dependency discovery
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
(def graph (dv/ns-vars->graph ['foo.db 'foo.service 'foo.web]))  ; scan namespaces for injectable vars
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
        {:keys [web-create-order]} (di/inject-all graph seed)]
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


#### Development support

Once you realize a dependency graph, you can create injected vars for easy calling at the REPL:

```clojure
(dv/create-vars! (di/inject-all graph seed) {:var-graph graph})  ; create injected vars

(dv/remove-vars! injected-vars)  ; accepts a list of var names (e.g. what create-vars! returns)
```

##### CIDER M-.

Assuming you have called `dime.var/create-vars!` with `{:var-graph graph}` option, you can also have `M-.` (meta-dot,
jump to source) support with CIDER. You can include the contents of (or call) [`dime-cider.el`](dime-cider.el) in your
`init.el` file.


## License

Copyright © 2016-2018 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
