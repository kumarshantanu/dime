;;
;; dime-cider.el
;;
;; Provides M-. (meta-dot, jump to source) support for DIME injected dependencies with Emacs CIDER.
;;
;; Setup: Include this file's content in `init.el` or make a call to `dime-cider.el` from the init script.
;;
;; Usage: You must call `dime.var/create-vars!` with option `{:var-graph graph}` in advance to make it work.
;;


(defun cider-dime-var-info (f &rest args)
  (or
   ;; try DIME navigation
   (ignore-errors
     (let* (;; find the symbol at M-.
            (sym-at-point (car args))
            ;; find defn var name
            (top-level-var (thread-first (cider-nrepl-sync-request:eval (cider-defun-at-point)
                                                                        (cider-current-connection)
                                                                        (cider-current-ns))
                             (nrepl-dict-get  "value")
                             (split-string "/")
                             cadr))
            ;; find source var symbol
            (source-var-name
             (when top-level-var
               (thread-first (format "(dime.var/sym->source '%s '%s '%s)"
                                     (cider-current-ns)
                                     top-level-var
                                     sym-at-point)
                 (cider-nrepl-sync-request:eval)
                 (nrepl-dict-get "value")))))
       (when (and source-var-name
                  (not (string-match-p "nil" source-var-name)))
         ;; get var info of target fully-qualified var name
         (let* ((source-var-pair (split-string source-var-name "/"))
                (var-info (cider-nrepl-send-sync-request `("op" "info"
                                                           "ns" ,(car source-var-pair)
                                                           "symbol" ,(cadr source-var-pair)))))
           (if (member "no-info" (nrepl-dict-get var-info "status"))
               nil
             var-info)))))
   ;; else fallback on default behavior
   (apply f args)))


(advice-add #'cider-var-info :around #'cider-dime-var-info)
