;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns foo.service
  (:require
    [dime.core :as di]))


(defn find-items
  "This has an implicit inject name, hence will be overridden by the explicit one in foo.db namespace."
  [item-ids]
  :mock-items)


(defn ^:inject recommend-products
  "Return item IDs for specified user ID."
  [^:inject items-cache user-id]
  (let [item-ids (get items-cache user-id)]
    (find-items item-ids)))


(di/definj service-browse-items
  [find-items recommend-products]
  [user-id]
  (let [item-ids (recommend-products user-id)]
    (find-items item-ids)))


(defn ^{:inject :svc/create-order} service-create-order
  [^:inject [find-items db-create-order]
   user-details order-details]
  (let [item-ids   (find-items (:item-ids order-details))
        order-data {:order-data :dummy}]  ; prepare order-data
    (db-create-order order-data)))
