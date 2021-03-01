;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.var-test
  (:require
    [clojure.test :refer :all]
    [dime.core :as di]
    [dime.var  :as dv]
    [foo.db])
  (:import
    [clojure.lang ArityException]))


(deftest test-vars-inject
  (let [di-graph (dv/vars->graph [#'foo.db/make-conn-pool
                                  #'foo.db/make-conn-pool2
                                  #'foo.db/db-find-items
                                  #'foo.db/db-create-order])
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
        (is (= 2 @foo.db/init-count) "Initialization must happen exactly twice")
        (is (= :dummy-pool                 connection-pool))
        (is (= {:items :dummy}             (find-items :dummy)))
        (is (= {:created-order :dummy}     (db-create-order :dummy)))
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= :foo (db-create-order :dummy)) "Var must be associated with partial"))))
    (testing "pre-inject dissociates from var"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map {:pre-inject-processor (fn [pre-inject the-var args] @the-var)})
            {:keys [connection-pool
                    db-create-order
                    find-items]} injected]
        (is (= 2 @foo.db/init-count) "Initialization must happen exactly twice")
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= {:created-order :dummy} (db-create-order :dummy)) "Var must be dissociated with partial"))))))


(deftest test-ns-vars-inject
  (let [di-graph (dv/ns-vars->graph ['foo.web
                                     'foo.service
                                     'foo.db])
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
                    items-cache
                    service-browse-items
                    web-create-order]} injected
            service-create-order (:svc/create-order injected)]
        (is (= 2 @foo.db/init-count) "Initialization must happen exactly twice")
        (is (= :dummy-pool                 connection-pool))
        (is (= :dummy-cache                items-cache))
        (is (= {:items :mock-items}        (service-browse-items :dummy-user-id)))
        (is (thrown? ArityException        (service-browse-items :dummy-user-id :bad-arg)))
        (is (= {:items :dummy}             (find-items :dummy)))
        (is (= {:created-order :dummy}     (db-create-order :dummy)))
        (is (= {:created-order
                {:order-data :dummy}}      (service-create-order :dummy-user :dummy-order)))
        (is (= {:response :dummy-response} (web-create-order {})))
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= :foo (db-create-order :dummy)) "Var must be associated with partial"))))
    (testing "private :expose-"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map)]
        (is (not (contains? injected :recommend-products-impl)) "private exposed var is excluded")
        (is (contains? injected :svc/create-order) "regular (non-private) exposed var is included")))
    (testing "pre-inject dissociates from var"
      (reset! foo.db/init-count 0)
      (let [injected (di/inject-all di-graph seed-map {:pre-inject-processor (fn [pre-inject the-var args] @the-var)})
            {:keys [connection-pool
                    db-create-order
                    find-items
                    service-create-order
                    web-create-order]} injected]
        (is (= 2 @foo.db/init-count) "Initialization must happen exactly twice")
        (with-redefs [foo.db/db-create-order (constantly :foo)]
          (is (= {:created-order :dummy} (db-create-order :dummy)) "Var must be dissociated with partial"))))))


(deftest test-ns-vars-auto-qualify
  (let [di-graph (dv/ns-vars->graph {'foo.web     :web
                                     'foo.service :service
                                     'foo.db      :db})
        di-keys (set (keys di-graph))]
    (is (contains? di-keys :web/find-user)        "auto-qualified hinted (true) node-ID")
    (is (contains? di-keys :web/web-create-order) "auto-qualified inferred node-ID")
    (is (contains? di-keys :db/connection-pool)   "auto-qualified custom node-ID")
    (is (contains? di-keys :svc/create-order)     "qualified node-ID not auto-qualified")))
