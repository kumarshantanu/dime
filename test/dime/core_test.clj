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
    [clojure.lang ArityException]
    [dime.type    InjectableAttributes]))


(defn foo [a b c] [a b c])


(deftest test-partial
  (let [cc-p (partial foo :first)
        di-p (di/partial foo :first)]
    (with-redefs [foo (fn [a b c] {:data [a b c]})]
      (is (= (cc-p :second :third) [:first :second :third]))
      (is (= (di-p :second :third) {:data [:first :second :third]})))))


(def ten-times (di/inj ^{:inject :ten-multiplier} [^{:inject :multiplier} n] (* 10 n)))


(deftest test-inj
  (is (t/injectable? (di/inj [foo bar] [foo bar])))
  (is (t/injectable? ten-times)))


(deftest test-assoc-inj
  (is (contains? (di/assoc-inj {} ten-times) :ten-multiplier))
  (is (contains? (di/assoc-inj {} ten-times (di/inj ^{:inject :foo-bar} [foo bar] [foo bar])) :foo-bar)))


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
  (is (= 150 (di/inject ten-times {:multiplier 15})))
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


(deftest test-attr-map
  (let [f (fn [id ds] (reify t/Injectable
                        (valid? [_] true)
                        (iattrs [_] (t/map->InjectableAttributes {:inj-id  id
                                                                  :impl-id  (gensym)
                                                                  :dep-ids  ds
                                                                  :pre-inj  nil
                                                                  :post-inj nil}))
                        (inject   [_ deps pre] :injected)))
        g (reduce (fn [m x]
                    (assoc m (.-inj-id ^InjectableAttributes (t/iattrs x)) x))
            {} [(f :foo [:bar :baz])
                (f :bar [:x :y])
                (f :baz [:p :q])
                (f :qux [])])]
    (is (= {:foo [:bar :baz]
            :bar [:x :y]
            :baz [:p :q]
            :qux []}
          (di/attr-map g :dep-ids)))))
