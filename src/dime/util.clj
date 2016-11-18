;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.util)


(def ^:dynamic *expose-meta-key* :expose)


(def ^:dynamic *inject-meta-key* :inject)


(def ^:dynamic *pre-inject-meta-key* :pre-inject)


(def ^:dynamic *post-inject-meta-key* :post-inject)


(defn pre-inject-default
  "Arity-2 fn that may be used as a pre-inject fn for default behavior, that is to return the injectable intact."
  [injectable deps]
  injectable)


(defn post-inject-default
  "Arity-3 fn that may be used as a post-inject fn for default behavior, that is to return the injectable intact."
  [injected node-id deps]
  injected)


(defn post-inject-invoke
  "Arity-3 fn that may be used as a post-inject fn to invoke the injected as if it were a no-argument fn."
  [injected node-id deps]
  (injected))
