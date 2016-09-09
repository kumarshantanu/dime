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
    [dime.type :as t])
  (:import
    [clojure.lang Namespace]
    [dime.type    InjectableAttributes]))


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
  (iattrs   [the-var] (when (defn? the-var)
                        (let [var-meta (meta the-var)]
                          (t/map->InjectableAttributes
                            {:inj-id   (let [ik (get var-meta *inject-meta-key*)]
                                         (if (true? ik) (keyword (:name var-meta)) ik))
                             :impl-id  (symbol (str (.getName ^Namespace (:ns var-meta)) \/ (:name var-meta)))
                             :dep-ids  (->> (:arglists var-meta)
                                         (map (partial i/inject-prepare *inject-meta-key* the-var))
                                         (mapcat first)
                                         (map :inject-key)
                                         distinct)
                             :pre-inj  (get var-meta *pre-inject-meta-key*)
                             :post-inj (get var-meta *post-inject-meta-key*)}))))
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
        (binding [i/*original-fn* (-> (.-pre-inj ^InjectableAttributes (t/iattrs the-var))
                                    (pre the-var args))
                  i/*inject-args* args]
          (eval fn-maker))
        (catch ExceptionInInitializerError e  ; this may happen in Clojure 1.6 and below
          (throw (IllegalStateException.
                   (format "Failed to evaluate partial-expr for %s: %s" the-var fn-maker) e)))))))


(defn vars->graph
  "Given a bunch of vars, return a map of keywordized-name/var pairs."
  ([graph vars]
    (i/named-injectables->graph graph
      (reduce (fn [m the-var]
                (assoc m (:name (meta the-var)) the-var))
        {} vars)))
  ([vars]
    (vars->graph {} vars)))


(defn ns-vars->graph
  "Given a bunch of namespace symbols, scan them for public vars that may be injected with dependencies and return a
  map of keywordized-name/var pairs. Only public vars with at least one inject annotation are included."
  ([graph ns-symbols]
    (reduce (fn [graph ns-sym]
              (i/expected symbol? "a namespace symbol" ns-sym)
              (require ns-sym)
              (->> (ns-publics ns-sym)        ; select public vars only
                (filter #(defn? (second %)))  ; select vars created with defn only
                (filter (fn [[k v]]           ; select vars having at least one injection annotation
                          (let [dm (meta v)]
                            (->> (:arglists dm)
                              (apply concat)
                              (some #(get (meta %) *inject-meta-key*))
                              (or (get dm *inject-meta-key*))))))
                (i/named-injectables->graph graph)))
      graph ns-symbols))
  ([ns-symbols]
    (ns-vars->graph {} ns-symbols)))
