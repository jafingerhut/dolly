Steps I followed to create my modified version of the
`tools.namespace` library, and clone it into dolly (with namespaces
renamed).

Starting from root directory of dolly project, where its `project.clj`
file is:

    % cd copy-deps
    # Script tns-script.sh requires an Internet connection to reach
    # site github.com.  Retrieves a copy of the tools.namespace repo,
    # and then applies patches of mine to it.
    % ./tns-script.sh
    % cd ..

    # Use dolly itself to clone tools.namespace into itself.  The very
    # first time, this required that dolly depended upon a version of
    # tools.namespace that had not been copied into itself, in
    # project.clj.

    % lein repl

    (require '[dolly.clone :as c])

    (def dolly-root "/Users/jafinger/clj/dolly")
    (def src-path (str dolly-root "/src"))
    (def staging-path (str dolly-root "/staging"))
    (def tns-root (str dolly-root "/copy-deps/tools.namespace"))
    (def tns-src-path (str tns-root "/src/main/clojure"))

    (def dry-run {:dry-run? true :print? true})
    (def for-real {:dry-run? false :print? true})

    ;; Run once with dry-run to see what will happen.  Look it over to
    ;; see if it is reasonable, then run again with for-real to do the
    ;; copying.

    (def c (c/copy-namespaces-unmodified tns-src-path staging-path 'clojure.tools.namespace dry-run))
    (def c (c/copy-namespaces-unmodified tns-src-path staging-path 'clojure.tools.namespace for-real))

    ;; The copying above should make no modifications, so running a
    ;; diff command like the following from the dolly project root
    ;; directory in a command shell should show no differences.

    ;; diff -cr copy-deps/tools.namespace/src/main/clojure staging

    ;; Now move the files from the staging area into dolly's code and
    ;; rename the namespaces.

    (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.namespace 'dolly.copieddeps.tns.clojure.tools.namespace [src-path] dry-run)
    (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.namespace 'dolly.copieddeps.tns.clojure.tools.namespace [src-path] for-real)
