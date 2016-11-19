;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.util)


;; ----- metadata attribute keys -----


(def ^:dynamic *expose-meta-key* :expose)


(def ^:dynamic *inject-meta-key* :inject)


(def ^:dynamic *pre-inject-meta-key* :pre-inject)


(def ^:dynamic *post-inject-meta-key* :post-inject)


;; ----- injection utility fns -----


(defn pre-inject-identity
  "Identity pre injector - return the injectable object intact."
  [injectable deps]
  injectable)


(defn post-inject-identity
  "Identity post injector - return the injected object intact."
  [injected node-id deps]
  injected)


(defn post-inject-invoke
  "Invoke the injected as if it were a no-argument fn."
  [injected node-id deps]
  (injected))


(defn post-inject-alter
  "Bind specified var to the injected."
  [the-var]
  (fn [injected node-id deps]
    (alter-var-root the-var (constantly injected))))


(defn comp-pre-inject
  "Compose multiple pre injectors into one. Like clojure.core/comp, except it accepts arity-2 fns as pre-injectors."
  [& pre-injectors]
  (if (seq pre-injectors)
    (fn [injectable deps]
      (as-> pre-injectors $
        (map (fn [each-pre-injector deps]
               #(each-pre-injector % deps)) $ (repeat deps))
        (apply comp $)
        ($ injectable)))
    pre-inject-identity))


(defn comp-post-inject
  "Compose multiple post injectors into one. Like clojure.core/comp, except it accepts arity-3 fns as post-injectors."
  [& post-injectors]
  (if (seq post-injectors)
    (fn [injected node-id deps]
      (as-> post-injectors $
        (map (fn [each-post-injector node-id deps]
               #(each-post-injector % node-id deps)) $ (repeat node-id) (repeat deps))
        (apply comp $)
        ($ injected)))
    post-inject-identity))
