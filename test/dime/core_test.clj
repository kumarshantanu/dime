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
  (let [f-0 (di/inject sample-0 {})
        f-1 (di/inject sample-1 {})
        f-2 (di/inject sample-2 {:a 10 :b 20})
        f-3 (di/inject sample-3 {:a 10 :b {:b 20 :d 40}})]
    (is (= :sample (f-0)))
    (is (= [10 20] (f-1 10 20)))
    (is (= [10 20] (f-2)))
    (is (thrown? ArityException (f-2 30)))
    (is (thrown? ArityException (f-3)))
    (is (= [10 [20 40] 30]             (f-3 30)))
    (is (= [10 {:b 20 :d 40} 30 '(40)] (f-3 30 40)))))


(deftest test-vars-inject
  (let [di-graph (->> (di/vars->graph [#'foo.db/make-conn-pool
                                       #'foo.db/db-find-items
                                       #'foo.db/db-create-order])
                   (apply merge))
        seed-map {:db-host "localhost"
                  :db-port 3306
                  :username "dbuser"
                  :password "s3cr3t"}]
    (testing "default config"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map)
            {:keys [connection-pool
                    db-create-order
                    find-items]} injected]
        (is (= 1 @foo.db/init-count) "Initialization must happen exactly once")
        (is (= :dummy-pool                 connection-pool))
        (is (= {:items :dummy}             (find-items :dummy)))
        (is (= {:created-order :dummy}     (db-create-order :dummy)))
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= :foo (db-create-order :dummy)) "Var must be associated with partial"))))
    (testing "pre-inject dissociates from var"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map {:pre-inject (fn [the-var args] @the-var)})
            {:keys [connection-pool
                    db-create-order
                    find-items]} injected]
        (is (= 1 @foo.db/init-count) "Initialization must happen exactly once")
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= {:created-order :dummy} (db-create-order :dummy)) "Var must be dissociated with partial"))))))


(deftest test-ns-vars-inject
  (let [di-graph (->> (di/ns-vars->graph ['foo.web
                                          'foo.service
                                          'foo.db])
                   (apply merge))
        seed-map {:db-host "localhost"
                  :db-port 3306
                  :username "dbuser"
                  :password "s3cr3t"}]
    (testing "default config"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map)
            {:keys [connection-pool
                    db-create-order
                    find-items
                    service-create-order
                    web-create-order]} injected]
        (is (= 1 @foo.db/init-count) "Initialization must happen exactly once")
        (is (= :dummy-pool                 connection-pool))
        (is (= {:items :dummy}             (find-items :dummy)))
        (is (= {:created-order :dummy}     (db-create-order :dummy)))
        (is (= {:created-order
                {:order-data :dummy}}      (service-create-order :dummy-user :dummy-order)))
        (is (= {:response :dummy-response} (web-create-order {})))
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= :foo (db-create-order :dummy)) "Var must be associated with partial"))))
    (testing "pre-inject dissociates from var"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map {:pre-inject (fn [the-var args] @the-var)})
            {:keys [connection-pool
                    db-create-order
                    find-items
                    service-create-order
                    web-create-order]} injected]
        (is (= 1 @foo.db/init-count) "Initialization must happen exactly once")
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= {:created-order :dummy} (db-create-order :dummy)) "Var must be dissociated with partial"))))))
