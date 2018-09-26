;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns foo.db
  (:require
    [dime.core :as di]
    [dime.util :as du]
    [dime.var  :as dv]))


(def init-count (atom 0))


(defn ^{:expose :connection-pool
        :post-inject du/post-inject-invoke}
      make-conn-pool
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  (swap! init-count inc)
  :dummy-pool)


(dv/defconst ^{:expose :connection-pool2}
             make-conn-pool2
  "This is a factory function."
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  (swap! init-count inc)
  :dummy-pool2)


(di/definj ^{:post-inject du/post-inject-invoke} items-cache
  [connection-pool]
  []
  :dummy-cache)


(defn ^{:expose :find-items} db-find-items
  [^:inject connection-pool ^:inject connection-pool2 item-ids]
  {:items item-ids})


(defn db-create-order
  [^:inject connection-pool ^:inject connection-pool2 order-data]
  {:created-order order-data})
