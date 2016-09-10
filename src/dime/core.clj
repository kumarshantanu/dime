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
    [dime.internal :as i]
    [dime.type :as t])
  (:import
    [dime.type InjectableAttributes]))


(defmacro partial
  "Same as clojure.core/partial, except it is a macro and is friendly with vars - preserves associativity with vars.
  Useful to create partials out of vars instrumented using clojure.core/alter-var-root or clojure.core/with-redefs."
  [f & args]
  `(fn [& args#]
     (apply ~f ~@args args#)))


(defn inj*
  "Given an injection option map, dependency-keys and an arity-2 function to carry out injection, return an injectable."
  [{:keys [inject
           impl-id
           pre-inject
           post-inject]
    :or {impl-id (gensym "dime-generated/injectable-")}
    :as options} dep-ids f]
  (reify t/Injectable
    (valid? [_] true)
    (iattrs [_] (t/map->InjectableAttributes {:node-id  inject
                                              :impl-id  impl-id
                                              :dep-ids  dep-ids
                                              :pre-inj  pre-inject
                                              :post-inj post-inject}))
    (inject [_ deps pre] (f deps pre))))


(defmacro inj
  "Given an annotated argument vector create an injectable using the body of code. Pre-inject argument is ignored."
  [arg-vec & body]
  (i/expected vector? "argument vector" arg-vec)
  (let [deps-map (gensym "deps-map-")
        dep-ids  (->> arg-vec
                   (map (fn [a] (or
                                  (:inject (meta a))
                                  (when (symbol? a)
                                    (keyword a))
                                  (i/expected "argument either annotated with :inject, or a symbol" a))))
                   vec)
        bindings (->> dep-ids
                   (map (fn [k] `(if (contains? ~deps-map ~k)
                                   (get ~deps-map ~k)
                                   (i/expected (format "key '%s' in dependencies" ~k) (keys ~deps-map)))))
                   (interleave arg-vec)
                   vec)]
    `(inj*
       (let [call-meta# (i/whereami)]
         (merge {:impl-id (format "%s, %s: %d"
                            (:clj-varname call-meta#) (:file-name call-meta#) (:line-number call-meta#))}
           ~(meta arg-vec)))
       ~dep-ids
       (fn [~deps-map pre#]
         (let ~bindings
           ~@body)))))


(defn assoc-inj
  "Associate one or more injectables with their corresponding node-IDs into a map."
  ([m injectable]
    (if (t/valid? injectable)
      (assoc m (.-node-id ^InjectableAttributes (t/iattrs injectable)) injectable)
      m))
  ([m injectable & more]
    (->> (cons injectable more)
      (filter t/valid?)
      (mapcat (fn [i] [(.-node-id ^InjectableAttributes (t/iattrs i)) i]))
      (apply assoc m))))


(defn process-pre-inject
  "Return the result of applying pre-inject fn to remaining args if pre-inject is non-nil, pre-injected injectable
  otherwise. Default pre-inject processor.
  Arguments:
   pre-inject  ; pre-inject fn
   injectable  ; injectable to be injected with dependencies
   args        ; map to resolve dependencies from"
  [pre-inject injectable args]
  (if pre-inject
    (pre-inject injectable args)
    injectable))


(defn inject
  "Apply dependencies to the injectable and return the result."
  ([injectable deps {:keys [pre-inject-processor]
                     :or {pre-inject-processor process-pre-inject}
                     :as options}]
    (t/inject injectable deps pre-inject-processor))
  ([injectable deps]
    (inject injectable deps {})))


(defn process-post-inject
  "Return the result of applying post-inject fn to remaining args if post-inject is non-nil, injected instance
  otherwise. Default post-inject processor.
  Arguments:
   post-inject ; post-inject fn
   injected    ; the partial fn created from the injectable
   node-ID     ; node ID for the injectable
   resolved    ; resolved map of seed + dependencies so far"
  [post-inject injected node-id resolved]
  (if post-inject
    (post-inject injected node-id resolved)
    injected))


(defn inject-all
  "Given a map of node-ID/injectable pairs and seed data map, resolve/inject all dependencies and return a map of
  node-ID/partially-applied-function pairs."
  ([graph seed {:keys [post-inject-processor]
                :or {post-inject-processor process-post-inject}
                :as options}]
    (i/expected map? "a dependency graph as a map" graph)
    (i/expected map? "seed data to begin injection" seed)
    (reduce (fn inject-one [m [k injectable]]
              (let [post-inject (fn [m p] (-> (.-post-inj ^InjectableAttributes (t/iattrs injectable))
                                            (post-inject-processor p k m)))
                    inject-deps (fn [m] (if (contains? m k)         ; avoid duplicate resolution
                                          m
                                          (->> options              ; propagate options for `pre-inject` processing
                                            (inject injectable m)
                                            (post-inject m)
                                            (assoc m k))))]
                (->> (.-dep-ids ^InjectableAttributes
                                (t/iattrs injectable))              ; find dependencies of each injectable
                  (map (fn [each-dep-key]                           ; verify each dependency exists in seed/graph
                         (when-not (or (contains? seed each-dep-key)
                                     (contains? graph each-dep-key))
                           (i/expected (format "dependency %s (for %s %s) to exist among seed/graph keys"
                                         each-dep-key k injectable)
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


(defn attr-map
  "Given a map of node-IDs to injectables, return a map of node-IDs to f (arity-1 fn) applied to injectable attributes."
  [graph f]
  (reduce (fn [m [k injectable]]
            (assoc m k (f (t/iattrs injectable))))
    {} graph))
