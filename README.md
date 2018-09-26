# dime

[![Build Status](https://travis-ci.org/kumarshantanu/dime.svg)](https://travis-ci.org/kumarshantanu/dime)

Dependency Injection Made Easy for Clojure.
[This blog post](https://medium.com/@kumarshantanu/dependency-injection-with-clojure-using-dime-af57b140bd3f)
explains the premise.


## Rationale

Initializing and wiring up components in a non-trivial application could be a complex and brittle affair.
It could be stateful, repetitive, messy or all of those. Dime aims to make that process less error-prone,
repeatable and easy to reason about by implementing mostly-automated
[dependency injection/inversion](https://en.wikipedia.org/wiki/Dependency_inversion_principle).


### Goals

- Easy, push-model dependency injection
- Flexible and easy lifecycle management
- Thorough testability, avoiding [pitfalls](http://charsequence.blogspot.in/2016/12/mocking-with-var-redefinition.html)
- Avoid mutation (except in development)


### Caveats (Trade off)

- Opinionated in favor of decoupling
- Cascading dependencies
- Overhead of tagging all dependencies
- Cannot inject in multimethods out of the box


### Other work

The following projects take different approaches to dependency management:

- [Component](https://github.com/stuartsierra/component)
- [Mount](https://github.com/tolitius/mount)
- [Mount-lite](https://github.com/aroemers/mount-lite)
- [Integrant](https://github.com/weavejester/integrant)


## Usage

Clojars coordinates: `[dime "0.5.1"]`


See [Documentation](doc/intro.md)


## Discuss

Slack channel: [#dime](https://clojurians.slack.com/messages/CAJUKHCG0/) (you need an invitation from
http://clojurians.net/ to join the Clojurian Slack team)


## Development

Running tests:

```bash
$ lein do clean, test       # run tests in lowest supported Clojure version
```


## License

Copyright © 2016-2018 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
