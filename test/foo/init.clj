;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns foo.init
  (:require
    [dime.core :as di]
    [dime.var  :as dv]
    [dime.util :as du]))


(def graph (dv/ns-vars->graph ['foo.web
                               'foo.service
                               'foo.db]))


(def seed {:db-host "localhost"
           :db-port 3306
           :username "dbuser"
           :password "s3cr3t"})


(defn viz-payload
  []
  {:graph-data  (di/attr-map graph :dep-ids)
   :node-labels (di/attr-map graph #(str (:node-id %) \newline (:impl-id %)))
   :node-shapes (di/attr-map graph #(when (du/is-or-has? (:post-inj %) du/post-inject-invoke)
                                      :rectangle))})


(defn -main
  []
  ;; `inject-all` resolves/injects dependencies,
  ;; returning exposed keys associated with partially applied functions
  (let [{:keys [web-create-order]} (di/inject-all graph seed)]
    ;; now `web-create-order` can be invoked with just one argument
    (println
      (web-create-order {:uri "/create/1234"
                         :request-method :post
                         :body "item=sealring&partno=6789"}))))
