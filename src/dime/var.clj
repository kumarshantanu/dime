;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.var
  (:require
    [dime.internal :as i]
    [dime.type :as t]))


(defn defn?
  "Return true if argument is a var created using defn, false otherwise."
  [df]
  (and (var? df)
    (contains? (meta df) :arglists)))


(def ^:dynamic *inject-meta-key* :inject)


(def ^:dynamic *pre-inject-meta-key* :pre-inject)


(def ^:dynamic *post-inject-meta-key* :post-inject)


(extend-protocol t/Injectable
  clojure.lang.Var
  (valid?   [the-var] (defn? the-var))
  (id-key   [the-var] (get (meta the-var) *inject-meta-key*))
  (dep-keys [the-var] (->> (meta the-var)
                        :arglists
                        (map (partial i/inject-prepare *inject-meta-key* the-var))
                        (mapcat first)
                        (map :inject-key)
                        distinct))
  (inject   [the-var args pre]
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
        (binding [i/*original-fn* (-> (t/pre-inject-fn the-var)
                                    (pre the-var args))
                  i/*inject-args* args]
          (eval fn-maker))
        (catch ExceptionInInitializerError e  ; this may happen in Clojure 1.6 and below
          (throw (IllegalStateException.
                   (format "Failed to evaluate partial-expr for %s: %s" the-var fn-maker) e))))))
  (pre-inject-fn [the-var] (-> (meta the-var)
                             (get *pre-inject-meta-key*)))
  (post-inject-fn [the-var] (-> (meta the-var)
                              (get *post-inject-meta-key*))))


(defn vars->graph
  "Given a bunch of vars, return a vector [implicit explicit] of maps of keywordized-name/var pairs."
  ([[implicit explicit] vars]
    (i/named-injectables->graph [implicit explicit]
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
                (i/named-injectables->graph [implicit explicit])))
      [implicit explicit] ns-symbols))
  ([ns-symbols]
    (ns-vars->graph [{} {}] ns-symbols)))
