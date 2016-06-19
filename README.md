# dime

**Dependency Injection Made Easy for Clojure.**

Dime implements [dependency injection/inversion](https://en.wikipedia.org/wiki/Dependency_inversion_principle) by
creating partially applied functions in an inexpensive (boiler-plate free), mostly automated manner.


## Usage

Leiningen coordinates: `[dime "0.1.0-SNAPSHOT"]` (not on Clojars yet)


### Example

Consider a contrived order posting implementation with a decoupled design as shown below. You are supposed to write
code in a similar fashion (with meta data tags) in your application for automatic injection.


#### Annotated functions

Notice the meta data tags (`:inject`, `:post-inject`) used in the code.

```clojure
;; ---------------- in namespace foo.db ----------------

(defn ^{:inject :connection-pool
        :post-inject (fn [m f] (f))}  ; custom processing: execute the fn to obtain connection-pool
      make-conn-pool
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  :dummy-pool)

(defn ^{:inject :find-items} db-find-items
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

(defn find-user
  [session]
  :dummy-user)

(defn web-create-order
  [^:inject find-user ^:inject service-create-order web-request]
  (let [user-details (find-user (:session web-request))
        order-details {:order :dummy-order}
        created-order (service-create-order user-details order-details)]
    {:response :dummy-response}))
```


#### Dependency resolution

The code required to actually resolve and inject the dependencies is quite trivial.

```clojure
(require '[dime.core :as di])

(let [deps (->> (di/ns-vars->graph ['foo.db 'foo.service 'foo.web]) ;; scan namespaces for vars to be injected
             (apply merge))
      seed {:db-host "localhost"
            :db-port 3306
            :username "dbuser"
            :password "s3cr3t"}
      ;; `inject-all` resolves/injects dependencies, returning keys associated with partial functions
      {:keys [web-create-order]} (di/inject-all deps seed)]
  ;; now `web-create-order` needs to be called with only `web-request`
  ..)
```


### How it works

* The `:inject` meta data tag is required for all dependency arguments to be injected.
* The `defn` var names are keywordized to form their injection names unless overridden by the `:inject` tag.
* The `:inject` tags on `defn` arguments are matched against the `:inject` names of the `defn` vars.
* Arguments marked with `:inject` are looked up in seed data first, followed by other dependencies.
* The `:post-inject` tag is used for custom processing after partial fn is created. By default, no processing is done.


#### Injection options

`dime.core/inject-all` (and `dime.core/inject`) allows an option map argument to customize the runtime behavior.

* Option `:pre-index` may be useful to `deref` the vars (for faster dispatch) before making a partial fn
* Option `:post-inject-wrapper` may be useful to catch any exceptions arising from post-injection handlers


## License

Copyright © 2016 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
