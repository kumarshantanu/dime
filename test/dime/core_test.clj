;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.core-test
  (:require
    [clojure.test :refer :all]
    [dime.core :as di]
    [dime.type :as t]
    [foo.db])
  (:import
    [clojure.lang ArityException]))


(defn foo [a b c] [a b c])


(deftest test-partial
  (let [cc-p (partial foo :first)
        di-p (di/partial foo :first)]
    (with-redefs [foo (fn [a b c] {:data [a b c]})]
      (is (= (cc-p :second :third) [:first :second :third]))
      (is (= (di-p :second :third) {:data [:first :second :third]})))))


(defn sample-0
  []
  :sample)


(defn sample-1
  [a b]
  [a b])


(defn sample-2
  ([^:inject a ^:inject b]
    [a b]))


(defn sample-3
  ([^:inject a ^{:inject :b} {:keys [b d]} c]
    [a [b d] c])
  ([^:inject a ^:inject b c & more]
    [a b c more]))


(deftest test-inject
  (let [f-0 (di/inject #'sample-0 {})
        f-1 (di/inject #'sample-1 {})
        f-2 (di/inject #'sample-2 {:a 10 :b 20})
        f-3 (di/inject #'sample-3 {:a 10 :b {:b 20 :d 40}})]
    (is (= :sample (f-0)))
    (is (= [10 20] (f-1 10 20)))
    (is (= [10 20] (f-2)))
    (is (thrown? ArityException (f-2 30)))
    (is (thrown? ArityException (f-3)))
    (is (= [10 [20 40] 30]             (f-3 30)))
    (is (= [10 {:b 20 :d 40} 30 '(40)] (f-3 30 40)))))


(deftest test-dependency-graph
  (let [f (fn [id ds] (reify t/Injectable
                        (valid?   [_] true)
                        (id-key   [_] id)
                        (dep-keys [_] ds)
                        (inject   [_ deps pre] :injected)
                        (pre-inject-fn  [_] nil)
                        (post-inject-fn [_] nil)))
        g (reduce (fn [m x]
                    (assoc m (t/id-key x) x))
            {} [(f :foo [:bar :baz])
                (f :bar [:x :y])
                (f :baz [:p :q])])]
    (is (= {:x []
            :y []
            :p []
            :q []
            :foo [:bar :baz]
            :bar [:x :y]
            :baz [:p :q]}
          (di/dependency-graph g)))))
