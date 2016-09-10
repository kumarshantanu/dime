;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.type)


(defprotocol Injectable
  (valid? [_] "Return true if valid injectable, false otherwise.")
  (iattrs [_] "Return InjectableAttributes")
  (inject [_ deps pre] "Inject dependencies and return partially applied injectable. `pre` is pre-process executor."))


(defrecord InjectableAttributes
  [node-id  ; node ID - unique role/artifact ID in a graph
   impl-id  ; implementation ID - unique instance identifier
   dep-ids  ; dependency edge IDs
   pre-inj  ; arity-2 fn (injectable, deps) or nil for pre-inject processing
   post-inj ; arity-3 (injected, node-id, deps) fn or nil for post-inject processing
   ])


(defn injectable?
  "Return true if specified arg is a valid injectable, false otherwise."
  [x]
  (and (satisfies? Injectable x)
    (valid? x)))
