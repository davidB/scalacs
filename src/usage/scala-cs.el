;; sample integration of scalacs with emacs
;; append to load-path + (require 'scala-cs) in .emacs.
;; Author : Minh Do Boi

(require 'compile)
(require 'flymake)
(require 'scala-mode)

(add-hook 'scala-mode-hook 'flymake-mode)

(defun call-remote-cs ()
  (list "curl" (list "http://127.0.0.1:27616/compile"))
  )

(add-to-list 'flymake-allowed-file-name-masks '(".+\\.scala$" call-remote-cs))
(add-to-list 'flymake-err-line-patterns '("^.*compiler\t\\(.*\\)#\\([0-9]+\\),.*\t\\(.*\\)$" 1 2 nil 3))

(provide 'scala-cs)