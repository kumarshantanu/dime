# History and TODO

## TODO

* [TODO] Annotate a var (args, inject, pre/post-inject) at runtime (3rd party code)
* [TODO] Introduce injection contexts
  * [TODO] Identification mechanism for injectables per context
  * [TODO] Visibility control for injectables per context


## 0.4.0 / 2016-November-??

* Injection support in destructuring argument expressions for vars
  * Sequential destructuring (vector literal)
  * Associative destructuring (map literal)
* Common utility in `dime.util` namespace
  * Overridable injection attribute names
  * Default pre-inject and post-inject handlers
  * Post-inject invoke handler for singleton values
* Miscellaneous
  * Use injection attribute names defined in `dime.util`namespace
  * [BREAKING CHANGE] Drop support for special `:post-inject` annotation `:singleton` (because not composable)
* Var injectables
  * [TODO] Vars can override how to inject by specifying `:inject-with` metadata
  * Use attribute names in `dime.util` for reading var metadata
  * [TODO] Support for creating injectable alias to pre-existing ordinary vars
  * [TODO] Support for updating pre-existing var metadata with injection info
* Non-var injectables
  * Make `dime.core/inj` read metadata using attribute names in `dime.util`
  * [TODO] Custom, efficient injectable types as defrecord
  * [TODO] Discoverable as a var


## 0.3.0 / 2016-September-13

* [BREAKING CHANGE] Protocol `dime.type/Injectable` now returns attributes as `dime.type/InjectableAttributes`
  * Attribute `id-key` renamed to `node-id` (to uniquely represent the node in a graph)
  * New attribute: `:impl-id` to uniquely represent the injectable implementation
* Auto-qualify the var node-IDs by specifying a map instead of a vector to `dime.var/ns-vars->graph`:
  * Normal: `(dv/ns-vars->graph ['ns1 'ns2 'ns3])`, Override: `(dv/ns-vars->graph {'ns1 :web 'ns2 :biz 'ns3 :db})`
* The `:post-inject` annotation `:singleton` is now considered as `(fn [f k m] (f))`
* [BREAKING CHANGE] Function `dime.core/dependency-graph` dropped in favor of new function `dime.core/attr-map`
* Integration demo with `[lein-viz "0.3.0"]`
  * Node name to be borrowed from impl-ID instead of node-ID
  * Node shape dictated by `:post-inj` attribute (non-nil implies singleton)
* Utility to
  * Create injectables on the fly
  * Associate injectables with their IDs in a map


## 0.2.0 / 2016-July-19

* A protocol called `dime.type/Injectable` for injectable types
  * Covers obtaining injection metadata and carrying out injection
  * Vars (created with `clojure.core/defn`) implement this protocol as an implementation detail
* [BREAKING CHANGE] Now `dime.core/inject` is a fn and works with injectables only
* [BREAKING CHANGE] Var-related API moved to `dime.var`
* [BREAKING CHANGE] Now `dime.var/ns-vars->graph` picks vars having at least one inject annotation
* [BREAKING CHANGE] Vars/injectables may not be implicit anymore - everything is explicit
* A fn `dime.core/dependency-graph` to extract dependency-keys from a dependency graph of injectables


## 0.1.0 / 2016-June-23

* Support for annotated code via meta data
  * Dependency arguments: tag `:inject`
  * Partially applied `defn` var: tag `:inject`
  * Pre-inject processing: tag `:pre-inject`
  * Post-inject processing: tag `:post-inject`
  * Customizable tags via dynamic vars
* Support for discovering dependency
  * From specified `defn` vars
  * From specified namespaces (scanning them for `defn` vars)
* Support for resolving dependency
  * Seed data
  * Matching inject keys across arguments and vars
  * Pre-inject processing: option `:pre-inject-processor`
  * Post-inject processing: option `:post-inject-processor`
