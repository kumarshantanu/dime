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
    [dime.util :as du]))


(def init-count (atom 0))


(defn ^{:inject :connection-pool
        :post-inject du/post-inject-invoke}
      make-conn-pool
  [^:inject db-host ^:inject db-port ^:inject username ^:inject password]
  (swap! init-count inc)
  :dummy-pool)


(di/definj ^{:post-inject du/post-inject-invoke} items-cache
  [connection-pool]
  []
  :dummy-cache)


(defn ^{:inject :find-items} db-find-items
  [^:inject connection-pool item-ids]
  {:items item-ids})


(defn db-create-order
  [^:inject connection-pool order-data]
  {:created-order order-data})
