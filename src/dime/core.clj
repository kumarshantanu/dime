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
    [dime.type :as t]))


(defmacro partial
  "Same as clojure.core/partial, except it does not lose associativity with the var."
  [f & args]
  `(fn [& args#]
     (apply ~f ~@args args#)))


(defn process-pre-inject
  "Return the result of applying pre-inject fn to remaining args if pre-inject is non-nil, pre-injected var
  otherwise. Default pre-inject processor.
  Arguments:
   pre-inject  ; pre-inject fn
   the-var     ; the var to be injected with dependencies
   args        ; map to resolve dependencies from"
  [pre-inject the-var args]
  (if pre-inject
    (pre-inject the-var args)
    the-var))


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
   injected    ; the partial fn created from the var
   inject-key  ; inject key for the var
   resolved    ; resolved map of seed + dependencies so far"
  [post-inject injected inject-key resolved]
  (if post-inject
    (post-inject injected inject-key resolved)
    injected))


(defn inject-all
  "Given a map of name/var pairs and seed data map, resolve/inject all dependencies and return a map of
  name/partially-applied-function pairs."
  ([graph seed {:keys [post-inject-processor]
                :or {post-inject-processor process-post-inject}
                :as options}]
    (i/expected map? "a dependency graph as a map" graph)
    (i/expected map? "seed data to begin injection" seed)
    (reduce (fn inject-one [m [k the-var]]
              (let [post-inject (fn [m p] (-> (t/post-inject-fn the-var)
                                            (post-inject-processor p k m)))
                    inject-deps (fn [m] (if (contains? m k)         ; avoid duplicate resolution
                                          m
                                          (->> options              ; propagate options for `pre-inject` processing
                                            (inject the-var m)
                                            (post-inject m)
                                            (assoc m k))))]
                (->> (t/dep-keys the-var)                           ; find dependencies of each var
                  (map (fn [each-dep-key]                           ; verify each dependency exists in seed/graph
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
