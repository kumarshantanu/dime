;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.core
  (:refer-clojure :exclude [partial])
  (:require
    [dime.internal :as i]))


(defmacro partial
  "Same as clojure.core/partial, except it does not lose associativity with the var."
  [f & args]
  `(fn [& args#]
     (apply ~f ~@args args#)))


(defn defn?
  "Return true if argument is a var created using defn, false otherwise."
  [df]
  (and (var? df)
    (contains? (meta df) :arglists)))


(def ^:dynamic *inject-meta-key* :inject)


(def ^:dynamic *post-inject-meta-key* :post-inject)


(defn inject*
  "Given a var defined with defn and an argument map, look up the :inject metadata on arguments and return a partially
  applied function."
  ([the-var args {:keys [pre-inject]
                  :or {pre-inject (fn [the-var args] the-var)}}]
    (i/expected defn? "a var created with clojure.core/defn" the-var)
    (i/expected map? "a map" args)
    (let [name-sym (gensym)
          var-meta (meta the-var)
          prepared (->> (:arglists var-meta)
                     (map (partial i/inject-prepare *inject-meta-key* name-sym)))
          bindings (->> prepared
                     (mapcat first)
                     (mapcat (fn [{:keys [sym inject-key]}]
                               [sym `(i/get-val i/*inject-args* ~inject-key)])))
          body-exp (map second prepared)
          fn-maker `(let [~name-sym i/*original-fn*
                          ~@bindings]
                      (fn ~(->> (:name var-meta)
                             (str "partial-")
                             symbol)
                        ~@body-exp))]
      (try
        (binding [i/*original-fn* (pre-inject the-var args)
                  i/*inject-args* args]
          (eval fn-maker))
        (catch ExceptionInInitializerError e  ; this may happen in Clojure 1.6 and below
          (throw (IllegalStateException. (format "Failed to evaluate partial-expr for %s: %s" the-var fn-maker) e))))))
  ([the-var args]
    (inject* the-var args {})))


(defmacro inject
  "Given a symbol representing a var defined using defn and an argument map, return a function after injecting the
  annotated arguments."
  ([defn-sym args options]
    (i/expected symbol? "a symbol" defn-sym)
    `(inject* (var ~defn-sym) ~args ~options))
  ([defn-sym args]
    (i/expected symbol? "a symbol" defn-sym)
    `(inject* (var ~defn-sym) ~args)))


(defn named-vars->graph
  "Given a map of symbol/keyword names (presumably inferred) and vars, return a vector [implicit explicit] of name/var
  maps for implicitly and explicitly inject-annotated vars."
  ([[implicit explicit] name-var-map]
    (reduce (fn [[implicit explicit] [var-sym the-var]]
              (i/expected defn? "a var created using clojure.core/defn" the-var)
              (if-let [inject-key (get (meta the-var) *inject-meta-key*)]
                [implicit (assoc explicit inject-key the-var)]
                [(assoc implicit (keyword var-sym) the-var) explicit]))
      [implicit explicit] name-var-map))
  ([name-var-map]
    (named-vars->graph [{} {}] name-var-map)))


(defn vars->graph
  "Given a bunch of vars, return a vector [implicit explicit] of maps of keywordized-name/var pairs."
  ([[implicit explicit] vars]
    (named-vars->graph [implicit explicit]
      (reduce (fn [m the-var]
                (assoc m (:name (meta the-var)) the-var))
        {} vars)))
  ([vars]
    (vars->graph [{} {}] vars)))


(defn ns-vars->graph
  "Given a bunch of namespace symbols, scan them for public vars that may be injected with dependencies and return a
  map of keywordized-name/var pairs. Explicit inject names override/replace the implicit keywordized-names."
  ([[implicit explicit] ns-symbols]
    (reduce (fn [[implicit explicit] ns-sym]
              (i/expected symbol? "a namespace symbol" ns-sym)
              (require ns-sym)
              (->> (ns-publics ns-sym)        ; select public vars only
                (filter #(defn? (second %)))  ; select vars created with defn only
                (named-vars->graph [implicit explicit])))
      [implicit explicit] ns-symbols))
  ([ns-symbols]
    (ns-vars->graph [{} {}] ns-symbols)))


(defn inject-all
  "Given a map of name/var pairs and seed data map, resolve/inject all dependencies and return a map of
  name/partially-applied-function pairs."
  ([graph seed {:keys [post-inject-wrapper]
                :or {post-inject-wrapper (fn [f  ; post-inject fn
                                              m  ; resolved seed+dependencies so far
                                              k  ; inject key for the var
                                              p  ; the partial fn created from the var
                                              ] (f m k p))}
                :as options}]
    (i/expected map? "a dependency graph as a map" graph)
    (i/expected map? "seed data to begin injection" seed)
    (reduce (fn inject-one [m [k the-var]]
              (let [post-inject (fn [m p] (if-let [pi (get (meta the-var) *post-inject-meta-key*)]
                                            (post-inject-wrapper pi m k p)
                                            p))
                    inject-deps (fn [m] (if (contains? m k)             ; avoid duplicate resolution
                                          m
                                          (->> options                  ; propagate options for `pre-inject` processing
                                            (inject* the-var m)
                                            (post-inject m)
                                            (assoc m k))))]
                (->> (i/var-dep-inject-keys *inject-meta-key* the-var)  ; find dependencies of each var
                  (map (fn [each-dep-key]                               ; verify each dependency exists in seed/graph
                         (when-not (or (contains? seed each-dep-key)
                                     (contains? graph each-dep-key))
                           (i/expected (format "dependency %s (for %s %s) to exist among seed/graph keys"
                                         each-dep-key k the-var)
                             {:seed-keys (keys seed)
                              :graph-keys (keys graph)}))
                         each-dep-key))
                  (reduce (fn [mseed each-dep-key]
                            (cond
                              (contains? mseed each-dep-key) mseed  ; avoid duplicate resolution
                              (contains? graph each-dep-key) (->> [each-dep-key (get graph each-dep-key)]
                                                               (inject-one mseed)
                                                               (merge mseed))
                              :otherwise
                              (i/expected (format "key %s to exist among resolvable keys" each-dep-key)
                                {:resolved-keys (keys mseed)
                                 :graph-keys (keys graph)})))
                    m)
                  inject-deps)))
      seed graph))
  ([graph seed]
    (inject-all graph seed {})))
