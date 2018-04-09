;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.var
  (:require
    [clojure.string :as string]
    [dime.internal :as i]
    [dime.type :as t]
    [dime.util :as u])
  (:import
    [clojure.lang Namespace]
    [dime.type    InjectableAttributes]))


(defn defn?
  "Return true if argument is a var created using defn, false otherwise."
  [df]
  (and (var? df)
    (contains? (meta df) :arglists)))


(extend-protocol t/Injectable
  clojure.lang.Var
  (valid?   [the-var] (and (defn? the-var)             ; created with defn?
                        (let [var-meta (meta the-var)] ; has at least one injection annotation?
                          (->> (:arglists var-meta)
                            (apply concat)
                            (some #(get (meta %) u/*inject-meta-key*))
                            (or (get var-meta u/*expose-meta-key*))))))
  (iattrs   [the-var] (when (t/valid? the-var)
                        (let [var-meta (meta the-var)]
                          (t/map->InjectableAttributes
                            {:node-id  (let [ek (get var-meta u/*expose-meta-key*)]
                                         (if (or (true? ek) (nil? ek)) (keyword (:name var-meta)) ek))
                             :impl-id  (symbol (str (.getName ^Namespace (:ns var-meta)) \/ (:name var-meta)))
                             :dep-ids  (->> (:arglists var-meta)
                                         (map (partial i/inject-prepare u/*inject-meta-key* the-var))
                                         (mapcat first)
                                         (mapcat :inject-keys)  ; populated in internal.clj
                                         distinct)
                             :pre-inj  (get var-meta u/*pre-inject-meta-key*)
                             :post-inj (get var-meta u/*post-inject-meta-key*)}))))
  (inject   [the-var args pre]
    (i/expected defn? "a var created with clojure.core/defn" the-var)
    (i/expected map? "a map" args)
    (let [name-sym (gensym)
          var-meta (meta the-var)
          prepared (->> (:arglists var-meta)
                     (map (partial i/inject-prepare u/*inject-meta-key* name-sym)))
          bindings (->> prepared
                     (mapcat first)
                     (mapcat (fn [{:keys [sym injector-fn inject-keys]}]  ; populated in internal.clj
                               [sym `(let [iks# ~(vec inject-keys)]
                                       (->> iks#
                                         (mapv #(i/get-val i/*inject-args* %))
                                         (~injector-fn iks#)))])))
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
    (let [as-nameonly-str (fn [x]
                            (if (instance? clojure.lang.Named x)
                              (name x)
                              (str x)))
          as-var-kv-entry (fn [wrapper graph ns-sym]
                            (i/expected symbol? "a namespace symbol" ns-sym)
                            (require ns-sym)
                            (->> (ns-publics ns-sym)          ; select public vars only
                              (filter #(t/valid? (second %))) ; select valid injectable vars only
                              (map (fn [[k v]] [k (wrapper ns-sym v)]))
                              (i/named-injectables->graph graph)))
          qualify-node-id (fn [ns-sym the-var]
                            (reify t/Injectable
                              (valid? [_]
                                (t/valid? the-var))
                              (iattrs [_]
                                (when-let [attrs (t/iattrs the-var)]
                                  (update-in attrs [:node-id]
                                    (fn [node-id]
                                      (if (or (and (instance? clojure.lang.Named node-id) (namespace node-id))
                                            (and (string? node-id) (pos? (.indexOf ^String node-id (int \/)))))
                                        node-id
                                        (if-let [default-ns (get ns-symbols ns-sym)]
                                          (cond  ; attempt to qualify only keyword/symbol/string node-IDs
                                            (keyword? node-id) (keyword (as-nameonly-str default-ns) (name node-id))
                                            (symbol?  node-id) (symbol  (as-nameonly-str default-ns) (name node-id))
                                            (string?  node-id) (str (as-nameonly-str default-ns) \/ node-id)
                                            :otherwise         node-id)
                                          node-id))))))
                              (inject [_ deps pre]
                                (t/inject the-var deps pre))))]
      (if (map? ns-symbols)
       (reduce (partial as-var-kv-entry qualify-node-id) graph (keys ns-symbols))
       (reduce (partial as-var-kv-entry (fn [k iv] iv))  graph ns-symbols))))
  ([ns-symbols]
    (ns-vars->graph {} ns-symbols)))


(def ^{:doc "Last ns-prefix (string) used to create injected vars."
       :tag "java.lang.String"
       :private true
       :redef true} last-ns-prefix
  nil)


(defn sym->source
  "Given ns/var/dependency symbols, return the corresponding source - fully-qualified var name as a string. If not
  found, return nil."
  [ns-sym var-sym dep-sym]
  (i/expected symbol? "ns symbol"  ns-sym)
  (i/expected symbol? "var symbol" var-sym)
  (i/expected symbol? "dependency symbol" dep-sym)
  (require ns-sym)
  (i/when-let-multi [the-var  (find-var (symbol (str ns-sym \/ var-sym)))
                     var-meta (meta the-var)
                     inj-key  (loop [attr-maps (->> (:arglists var-meta)
                                                 (map (partial i/inject-prepare u/*inject-meta-key* the-var))
                                                 (mapcat first)
                                                 seq)]
                                (when attr-maps
                                  (let [attrs (first attr-maps)]
                                    (or
                                      (loop [syms (seq (:inject-syms attrs))
                                             keys (seq (:inject-keys attrs))]
                                        (when (and syms keys)  ; both are not-nil
                                          (if (= (first syms) dep-sym)
                                            (first keys)
                                            (recur (next syms) (next keys)))))
                                      (recur (next attr-maps))))))
                     prefix   last-ns-prefix
                     inj-ns   (if-let [ikns (namespace inj-key)]
                                (str prefix \. ikns)
                                prefix)
                     inj-var  (->> (name inj-key)
                                (str inj-ns \/)
                                symbol
                                find-var)
                     inj-meta (meta inj-var)
                     impl-id  (:impl-id inj-meta)]
    (str impl-id)))


(defn create-vars!
  "Create vars (and namespaces) for realized injectables, returning a collection of fully-qualified created var names."
  ([realized-graph]
    (create-vars! realized-graph {}))
  ([realized-graph {:keys [ns-prefix
                           var-graph]
                    :or {ns-prefix "injected"}}]
    (i/expected #(not= \. (first %)) "ns-prefix not beginning with ." ns-prefix)
    (i/expected #(not= \. (last %))  "ns-prefix not ending with ." ns-prefix)
    (i/expected #(= % (string/trim %)) "ns-prefix to have no whitespace" ns-prefix)
    (i/expected map? "map of realized injectables" realized-graph)
    (doseq [[k v] (seq realized-graph)]
      (i/expected keyword? "keyword node-ID for injectable" k))
    ;; update last ns-prefix
    (alter-var-root #'last-ns-prefix (fn [_] ns-prefix))
    ;; proceed with creating injected vars
    (->> (seq realized-graph)
      (sort-by first)
      (group-by (comp namespace first))
      seq
      (reduce (fn [all-names [ns-suffix coll]]
                (let [ns-str (if ns-suffix
                               (str ns-prefix \. ns-suffix)
                               ns-prefix)
                      var-ns (create-ns (symbol ns-str))]                  ; create namespace
                  (loop [coll (seq coll)
                         nams all-names]
                    (if (nil? coll)
                      nams
                      (let [[exposed-keyword realized-val] (first coll)
                            varname-str (name exposed-keyword)]
                        (as-> (get var-graph exposed-keyword) $
                          (conj {} (and $ (t/iattrs $)) (meta $))
                          (with-meta (symbol varname-str) $)
                          (intern var-ns $ realized-val))                  ; create var
                        (recur (next coll) (conj nams (str ns-str \/ varname-str))))))))
        []))))


(defn remove-vars!
  "Remove the vars (and namespaces) indicated by specified fully-qualified var names."
  [fully-qualified-var-names]
  (i/expected coll? "collection of var names" fully-qualified-var-names)
  (->> fully-qualified-var-names
    (map symbol)
    sort
    (group-by namespace)
    seq
    (map (fn [[each-ns-str name-str-coll]]
           (let [ns-sym (symbol each-ns-str)]
             (doseq [each-varname-str name-str-coll]
               (ns-unalias ns-sym (symbol each-varname-str)))  ; remove each var
             (remove-ns ns-sym))))                             ; remove the namespace
    dorun))
