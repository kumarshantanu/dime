;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns foo.web)


(defn find-user
  [session]
  :dummy-user)


(defn web-create-order
  [^:inject find-user ^:inject service-create-order web-request]
  (let [user-details (find-user (:session web-request))
        order-details {:order :dummy-order}
        created-order (service-create-order user-details order-details)]
    {:response :dummy-response}))
