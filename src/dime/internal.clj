;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.internal
  (:require
    [clojure.string :as string]
    [dime.type :as t])
  (:import
    [dime.type InjectableAttributes]))


;; ----- error reporting -----


(defn expected
  "Throw illegal input exception citing `expectation` and what was `found` did not match. Optionally accept a predicate
  fn to test `found` before throwing the exception."
  ([expectation found]
    (throw (IllegalArgumentException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


;; ----- echo diagnostics -----


(defn echo
  "Print all message tokens, returning nil."
  [x & more]
  (apply println "[Echo]" x more))


(defn echof
  [fmt & args]
  (-> (apply format (str fmt) args)
    echo))


(defn echo->
  "Echo message and value in a -> form, returning the value. First arg is considered the value, rest as message tokens."
  [x & more]
  (->> [(pr-str x)]
    (concat (and (seq more) (concat more [\:])))
    (apply echo))
  x)


(defn echo->>
  "Echo message and value in a ->> form, returning the value. Last arg is considered the value, rest as message tokens."
  [x & more]
  (let [all (concat [x] more)]
    (apply echo-> (last all) (butlast all))))


;; ----- helpers -----

(defn named-injectables->graph
  "Given a map of node-IDs (presumably inferred) to injectables, return a map of node-ID/injectable pairs."
  ([graph name-injectable-map]
    (reduce (fn [graph [node-id injectable]]
              (expected t/injectable? "a valid injectable" injectable)
              (let [inject-key (or (.-node-id ^InjectableAttributes (t/iattrs injectable))
                                 (keyword node-id))]
                (when (contains? graph inject-key)
                  (throw (IllegalArgumentException.
                           (format "Duplicate injectable %s found in the graph. Existing: %s, New: %s"
                             inject-key (pr-str (get graph inject-key)) (pr-str injectable)))))
                (assoc graph inject-key injectable)))
      graph name-injectable-map))
  ([name-injectable-map]
    (named-injectables->graph {} name-injectable-map)))


(defmacro whereami
  "Return a map of attributes pointing to the call-site. May be useful for debugging. Sample return value is below:
  {:clj-varname \"foo.core/bar\",
   :method-name \"invoke\",
   :file-name   \"core.clj\",
   :class-name  \"foo.core$bar\",
   :line-number 21}"
  []
  `(let [e# (Exception.)
         ^StackTraceElement ste# (aget (.getStackTrace e#) 0)]
     ;; (.printStackTrace e#) ; uncomment this line to inspect stack trace
     {:class-name  (.getClassName  ste#)
      :file-name   (.getFileName   ste#)
      :line-number (.getLineNumber ste#)
      :method-name (.getMethodName ste#)
      :clj-varname (.replace (.getClassName  ste#) \$ \/)}))


;; ----- var helpers -----


(def ^:dynamic *original-fn* nil)


(def ^:dynamic *inject-args* nil)


(defn arg-info
  "Return a map of argument info. Helper for `inject-prepare`."
  [inject-meta-key arg]
  (let [info-map (fn [injector-fn inject-keys]
                   {:arg arg
                    :sym (gensym (if injector-fn "inject-" "arg-"))
                    :injector-fn injector-fn  ; non-nil injector-fn implies injectable dependency
                    :inject-keys inject-keys})
        no-infer (fn [a] (throw (ex-info (str "Cannot infer inject key for argument " (pr-str a))
                                  {:argument a})))]
    (if-let [inject-tag (get (meta arg) inject-meta-key)]
      (if (true? inject-tag)
        (cond
          (symbol? arg) (info-map (fn [ks vs] (first vs)) [(keyword arg)])
          (vector? arg) (->> (take-while (complement #{'& :as}) arg)
                          (map (fn [param]
                                 (let [inject-name (get (meta param) inject-meta-key)]
                                   (expected (comp not false?) "non-false :inject tag"
                                     inject-name)
                                   (if (and inject-name (not (true? inject-name)))
                                     inject-name
                                     (if (symbol? param)
                                       (keyword param)
                                       (no-infer param))))))
                          (info-map (fn [ks vs] (vec vs))))
          (map? arg)    (->> (seq arg)
                          (filter (comp not keyword? first))
                          (map second)
                          (concat (map keyword (:keys arg)))
                          (concat (map symbol (:syms arg)))
                          (concat (map str (:strs arg)))
                          (info-map zipmap))
          :otherwise    (no-infer arg))
        (info-map (fn [ks vs] (first vs)) [inject-tag]))
      (info-map nil nil))))


(defn inject-prepare
  "Given a defn var-name and arglist return a vector [inject-args fn-body] where inject-args is a collection of
  dependencies (each of them represented as a map) and fn-body is the body expression for partial fn."
  [inject-meta-key name-sym arglist]
  (let [var-args?   (some #(= % '&) arglist)
        var-argsym  (when var-args? (gensym "more-"))
        fixed-args  (->> arglist
                      (take-while #(not= % '&))   ; fixed args only
                      (map (partial arg-info inject-meta-key))
                      vec)
        inject-args (->> fixed-args
                      (filter :injector-fn))
        expose-syms (as-> fixed-args $
                      (remove :injector-fn $)
                      (mapv :sym $)
                      (concat $ (when var-args? ['& var-argsym]))
                      (vec $))
        invoke-syms (as-> fixed-args $
                      (map :sym $)
                      (concat $ (when var-args? [var-argsym])))]
    [inject-args (if var-args?
                   `(~expose-syms (apply ~name-sym ~@invoke-syms))
                   `(~expose-syms (~name-sym ~@invoke-syms)))]))


(defn get-val
  "Given map m, find and return the value of key k. Throw IllegalArgumentException when specified key is not found."
  [m k]
  (if (contains? m k)
    (get m k)
    (throw (IllegalArgumentException.
             (format "Key %s not found in keys %s" k (try (sort (keys m))
                                                       (catch ClassCastException _ (keys m))))))))
