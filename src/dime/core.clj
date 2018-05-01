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
    [clojure.set   :as set]
    [dime.internal :as i]
    [dime.type :as t]
    [dime.util :as u])
  (:import
    [clojure.lang ArityException]
    [dime.type InjectableAttributes]))


(defmacro partial
  "Same as clojure.core/partial, except it is a macro and is friendly with vars - preserves associativity with vars.
  Useful to create partials out of vars instrumented using clojure.core/alter-var-root or clojure.core/with-redefs."
  [f & args]
  `(fn [& args#]
     (apply ~f ~@args args#)))


(defn inj*
  "Given an injection option map, dependency-keys and an arity-2 function to carry out injection, return an injectable."
  [{:keys [expose
           impl-id
           pre-inject
           post-inject]
    :or {impl-id (gensym "dime-generated/injectable-")}
    :as options} dep-ids f]
  (reify t/Injectable
    (valid? [_] true)
    (iattrs [_] (t/map->InjectableAttributes {:node-id  expose
                                              :impl-id  impl-id
                                              :dep-ids  dep-ids
                                              :pre-inj  pre-inject
                                              :post-inj post-inject}))
    (inject [_ deps pre] (f deps pre))))


(defmacro inj
  "Given injection metadata and an annotated argument vector create an injectable using the body of code. Injection
  metadata has the keys `:inject`, `:impl-id` and `:post-inject`."
  [inj-meta arg-vec & body]
  (i/expected (some-fn map? nil?) "injection metadata as a map" inj-meta)
  (i/expected vector? "argument vector" arg-vec)
  (let [deps-map (gensym "deps-map-")
        dep-ids  (->> arg-vec
                   (map (fn [a] (or
                                  (get (meta a) u/*inject-meta-key*)
                                  (when (symbol? a)
                                    (keyword a))
                                  (i/expected
                                    (format "argument either annotated with %s, or a symbol" u/*inject-meta-key*) a))))
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
           ~inj-meta))
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


(defmacro definj
  "Define an injectable defrecord that acts as a function after it is instantiated."
  [sym deps args & body]
  ;; validate sym
  (i/expected symbol? "a symbol" sym)
  (i/expected (comp nil? namespace) "unqualified symbol" sym)
  ;; validate deps
  (i/expected vector? "a vector" deps)
  (i/expected #(every? symbol? %) "a vector of symbols" deps)
  (let [bodies (cond
                 (vector? args) (list (cons args body))  ; no argument overloading, we have single arity
                 (list? args)   (cons args body)         ; one or more arities
                 :otherwise     (i/expected "valid function body" (cons args body)))]
    ;; validate args
    (doseq [[args body] bodies]
      (i/expected vector? "argument vector" args)
      (i/expected #(every? (partial not= '&) %) "argument vector without variable args" args)
      (i/expected #(<= (count %) 20) "argument vector of 20 args or less" args))
    (let [sym-meta (meta sym)
          factory  (symbol (str "->" sym))
          inj-deps (fn [m] (if (get m u/*inject-meta-key*)
                             m
                             (assoc m u/*inject-meta-key* true)))
          apply-to (let [args (gensym "var-args-")
                         anth (fn [n]
                                (->> (range n)
                                  (map (fn [i] `(nth ~args ~i)))))]
                     `(applyTo [this# ~args]
                        (case (count ~args)
                          0  (.invoke this#)
                          1  (.invoke this# (first ~args))
                          2  (.invoke this# (first ~args) (second ~args))
                          3  (.invoke this# ~@(anth 3))
                          4  (.invoke this# ~@(anth 4))
                          5  (.invoke this# ~@(anth 5))
                          6  (.invoke this# ~@(anth 6))
                          7  (.invoke this# ~@(anth 7))
                          8  (.invoke this# ~@(anth 8))
                          9  (.invoke this# ~@(anth 9))
                          10 (.invoke this# ~@(anth 10))
                          11 (.invoke this# ~@(anth 11))
                          12 (.invoke this# ~@(anth 12))
                          13 (.invoke this# ~@(anth 13))
                          14 (.invoke this# ~@(anth 14))
                          15 (.invoke this# ~@(anth 15))
                          16 (.invoke this# ~@(anth 16))
                          17 (.invoke this# ~@(anth 17))
                          18 (.invoke this# ~@(anth 18))
                          19 (.invoke this# ~@(anth 19))
                          20 (.invoke this# ~@(anth 20))
                          (.invoke this# ~@(anth 20) (object-array (drop 20 ~args))))))
          matching (map (fn [[args & body]]
                       (let [arg-syms (repeatedly (count args) (partial gensym "arg-"))
                             bindings (interleave args arg-syms)]
                         `(~'invoke [this# ~@arg-syms] (let [~@bindings] ~@body))))
                     bodies)
          no-match (let [arity-set (->> bodies
                                     (map (comp count first))
                                     set)]
                     (->> arity-set
                       (set/difference (set (range 20)))
                       (map (fn [bad-arity]
                              `(~'invoke [this# ~@(repeatedly bad-arity gensym)]
                                 (throw (ArityException.
                                          ~(int bad-arity) ~(str sym " accepting " arity-set " args"))))))))]
      `(let [post-inj# (get ~sym-meta ~(do u/*post-inject-meta-key*) u/post-inject-identity)]
         (defrecord ~sym ~deps
           clojure.lang.IFn
           ~apply-to
           ~@matching
           ~@no-match)
         ;; update metadata to make it look like a defn var
         (alter-meta! (var ~factory) merge ~sym-meta
           {~(do u/*expose-meta-key*) ~(get (meta sym) u/*expose-meta-key* (keyword sym))
            :definj      true
            :arglists    '~(list (mapv #(vary-meta % inj-deps) deps))
            :post-inject (fn [f# k# m#]
                           (post-inj# (u/post-inject-invoke f# k# m#) k# m#))})))))


(defn process-pre-inject
  "Return the result of applying pre-inject fn to remaining args if pre-inject is non-nil, pre-injected injectable
  otherwise. Default pre-inject processor.
  Arguments:
   pre-inject  ; pre-inject fn, or nil, or a collection of pre-inject fns
   injectable  ; injectable to be injected with dependencies
   args        ; map to resolve dependencies from"
  [pre-inject injectable args]
  (if pre-inject
    (if (coll? pre-inject)
      (reduce (fn [pre-injected each-pre-inject] (each-pre-inject pre-injected args))
        injectable pre-inject)
      (pre-inject injectable args))
    injectable))


(defn inject
  "Apply dependencies to the injectable and return the result."
  ([injectable deps {:keys [pre-inject-processor]
                     :or {pre-inject-processor process-pre-inject}
                     :as options}]
    (if (t/valid? injectable)
      (t/inject injectable deps pre-inject-processor)
      (throw (IllegalArgumentException. (str "Not a valid injectable: " injectable)))))
  ([injectable deps]
    (inject injectable deps {})))


(defn process-post-inject
  "Return the result of applying post-inject fn to remaining args if post-inject is non-nil, injected instance
  otherwise. Default post-inject processor.
  Arguments:
   post-inject ; post-inject fn, or nil, or a collection of post-inject-fns
   injected    ; the partial fn created from the injectable
   node-ID     ; node ID for the injectable
   resolved    ; resolved map of seed + dependencies so far"
  [post-inject injected node-id resolved]
  (if post-inject
    (if (coll? post-inject)
      (reduce (fn [post-injected each-post-inject] (each-post-inject post-injected node-id resolved))
        injected post-inject)
      (post-inject injected node-id resolved))
    injected))


(defn inject-all
  "Given a map of node-ID/injectable pairs and seed data map, resolve/inject all dependencies and return a map of
  node-ID/injected pairs."
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
