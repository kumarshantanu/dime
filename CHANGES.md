# History and TODO

## TODO

* [TODO] Introduce injection contexts
  * [TODO] Identification mechanism for injectables per context
  * [TODO] Visibility control for injectables per context


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
