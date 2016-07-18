;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns dime.type)


(defprotocol Injectable
  (valid?   [_] "Return true if valid injectable, false otherwise.")
  (id-key   [_] "Return identifier key of the injectable.")
  (dep-keys [_] "Return all dependency keys of the injectable.")
  (inject   [_ deps pre] "Inject dependencies and return partially applied injectable. `pre` is pre-process executor.")
  (pre-inject-fn  [_] "Return arity-2 fn (injectable, deps) or nil for pre-inject processing.")
  (post-inject-fn [_] "Return arity-3 (injected, id-key, deps) fn or nil for post-inject processing."))


(defn injectable?
  "Return true if specified arg is a valid injectable, false otherwise."
  [x]
  (and (satisfies? Injectable x)
    (valid? x)))
