# History and TODO

## TODO

* [TODO - BREAKING CHANGE] Drop support for Clojure 1.5, 1.6
  * [Idea] Support for ClojureScript
* [TODO] Instrumentation support for `definj`
* [TODO] Use same type hints in exposed fn dependencies/args as in original fns
* [TODO] Support for drop-in replacement for Clojure facilities
  * [TODO] `dime.dropin/defmulti` Support for multi-methods
  * [TODO] `dime.dropin/defn` Support for non-declarative injection (scanning code)
* [TODO] Annotate a var (args, inject, pre/post-inject) at runtime (3rd party code)
* [TODO] Support for ClojureScript/ClojureCLR
* [TODO] Introduce injection contexts
  * [TODO] Support for `:expose` annotation to be associated with one or more keys
  * [TODO] Identification mechanism for injectables per context, e.g. `:context [:db :global]`
  * [TODO] Visibility control for injectables per context
* [Idea] Pervasive support for private exposure
  - dime.core/definj
  - dime.var/defconst


## [WIP] 0.5.2 / 2021-March-??

* Fix #2 - Add the ability to mark injectables as non-public
  - Expose vars privately with metadata key `:expose-` (instead of `:expose`)
  - Add dynamic var `dime.util/*expose-private-key*` as private-exposure meta key
* [Todo] Documentation
  - Documentation for private exposure
  - Cljdoc badge


## 0.5.1 / 2018-September-26

* Add macro `dime.var/defconst` as a shorthand to create singletons
  - All arguments must be injected


## 0.5.0 / 2018-May-01

* Add support for creating/removing virtual namespaces with injected vars
  - `dime.var/create-vars!`
  - `dime.var/remove-vars!`
  - `dime.var/sym->source` for tools: navigation support
* Implement `clojure.lang.IFn/applyTo` in `definj`
* Add `dime-cider.el` script for Emacs-CIDER `M-.` assistance


## 0.4.0 / 2016-November-24

* Common utility in `dime.util` namespace
  * Overridable injection attribute names
  * Identity (default) pre-inject and post-inject handlers
  * Post-inject invoke handler for singleton values
  * Post-inject handler to update a var with injected object
* Miscellaneous
  * [BREAKING CHANGE] Replace exposure metadata tag `:inject` with `:expose` for clarity
  * [BREAKING CHANGE] Drop support for `:post-inject` annotation `:singleton` (not composable)
  * Use injection attribute names defined in `dime.util` namespace
  * Pre and post injectors can now be either a fn or a collection of fns to be applied left to right
* Var injectables
  * Injection support in destructuring argument expressions for vars
    * Sequential destructuring (vector literal)
    * Associative destructuring (map literal)
  * Use attribute names in `dime.util` for reading var metadata
* Non-var injectables
  * [BREAKING CHANGE] Accept injection attributes as first argument in `dime.core/inj`
  * Make `dime.core/inj` read metadata using attribute names in `dime.util`
  * Efficient injectables implemented with `defrecord`, discoverable as vars


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
