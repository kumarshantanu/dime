# History and TODO

## 0.1.0 / 2016-June-??

* Support for annotated code via meta data
  * Dependency arguments: tag `:inject`
  * Partially applied `defn` var: tag `:inject`
  * Post-inject processing: tag `:post-inject`
  * Customizable tags via dynamic vars
* Support for discovering dependency
  * From specified `defn` vars
  * From specified namespaces (scanning them for `defn` vars)
* Support for resolving dependency
  * Seed data
  * Matching inject keys across arguments and vars
  * Pre-inject processing: option `:pre-inject`
  * Post-inject processing: option `:post-inject-wrapper`
