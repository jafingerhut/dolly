(ns dolly.clone
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.namespace.move :as move]
            [dolly.namespace :as ns])
  (:import (java.io File)))


(defn abbreviate-path
  "If the string pathname begins with the string to-abbrev (followed
by the File path separator string), replace that portion of the path
name with the abbreviation abbrev (followed by the FIle path separator
string) and return that.  Otherwise return pathname."
  [^String pathname to-abbrev abbrev]
  (if (and (string? abbrev)
           (.startsWith pathname (str to-abbrev File/separator)))
    (str abbrev (subs pathname (count to-abbrev)))
    pathname))


(defn copy-ns-file
  "Copies the .clj source file (found relative to source-path) for the
namespace named by the symbol ns-sym to a file for the same namespace,
but relative to dest-path.

WARNING: This function can overwrite any source file that was already
at the destination!  Make sure you have a backup or version control."
  [source-path ns-sym dest-path opts]
  (let [ns-fname (#'move/ns-file-name ns-sym)
        old-file (io/file source-path ns-fname)
        new-file (io/file dest-path ns-fname)]
    (when (:print? opts)
      (println (format "mkdir %s"
                       (abbreviate-path (str (.getParentFile new-file))
                                        dest-path (:dest-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (.mkdirs (.getParentFile new-file)))
    (when (:print? opts)
      (println (format "copy %s %s"
                       (abbreviate-path (str old-file) source-path
                                        (:source-path-abbrev opts))
                       (abbreviate-path (str new-file) dest-path
                                        (:dest-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (io/copy old-file new-file))))


(defn namespace-to-copy? [ns root-ns]
  (or (= ns root-ns)
      (let [^String ns-str (str ns)
            ^String root-ns-str (str root-ns ".")]
        (.startsWith ns-str root-ns-str))))


(defn copy-namespaces-unmodified
  "Scan through all files in directory source-path or one of its
subdirectories.  For every .clj source file that contains an ns
declaration with a namespace that is exactly ns-sym, or begins with
ns-sym followed by a dot, copy them unmodified to a file that is
rooted at the directory dest-path, and has the same relative path name
after dest-path that the original does after source-path.

The namespaces will first be checked that they correspond with the
file names in which they occur.  If they do not, an error is returned
and no files are copied.

For example, if source-path is /source/path and it contains a file
/source/path/one/two.clj, it will be an error if the namespace inside
is anything other than 'one.two'."
  [source-path ns-sym dest-path opts]
  (let [ns-info (ns/namespaces-in-dirs {:paths [source-path]})]
    (if (:err ns-info)
      ns-info
      (do
        (when (:print? opts)
          (println (format "Copy Clojure source files in namespace %s unmodified\n  from source path S='%s'\n  to dest path D='%s'"
                           ns-sym source-path dest-path)))
        (doseq [[file ns] (:clojure.tools.namespace.file/filemap ns-info)]
          (if (namespace-to-copy? ns ns-sym)
            (copy-ns-file source-path ns dest-path
                          (merge opts {:source-path-abbrev "S"
                                       :dest-path-abbrev "D"}))
            (when (:print? opts)
              (println (format "Don't copy namespace %s from S since it is not a 'sub-namespace' of %s"
                               ns ns-sym)))))))))


(comment
(require '[dolly.clone :as c])
(def tns-path "/Users/jafinger/clj/dolly/copy-deps/tools.namespace/src/main/clojure")
(def staging-path "/Users/jafinger/clj/dolly/staging")
(def o1 (c/copy-namespaces-unmodified tns-path 'clojure.tools.namespace staging-path {:dry-run? true :print? true}))
;;(print (apply str (:dry-run-msgs o1)))
(def o2 (c/copy-namespaces-unmodified tns-path 'clojure.tools.namespace.dir staging-path {:dry-run? true :print? true}))
;;(print (apply str (:dry-run-msgs o2)))
)


;;(defn copy-namespaces
;;  "Copy all .clj source files (found relative to source-path) for the
;;namespaces that have names beginning with old-sym.  Copy them to new
;;file with namespaces that begin with new-sym.  Replace all occurrences
;;of the old name with the new name in all Clojure source files found in
;;dirs.
;;
;;This is a purely textual transformation.  It does not work on
;;namespaces require'd or use'd from a prefix list.
;;
;;WARNING: This function modifies your source files!  Make sure you have
;;a backup or version control."
;;  [source-path old-sym dest-path new-sym dirs]
;;  (let [ns-info (ns/namespaces-in-dirs {:paths [source-path]})]
;;    (if (:err ns-info)
;;      ns-info
;;      (do
;;        (doseq [[file ns] (:clojure.tools.namespace.file/filemap ns-info)]
;;          (when (namespace-to-copy? ns old-sym)
;;            (copy-ns-file file ns old-sym new-sym source-path)))
;;        (doseq [file (#'move/clojure-source-files dirs)]
;;          (#'move/update-file file #'move/replace-ns-symbol
;;                              old-sym new-sym))))))


(defn move-ns-file
  "Copied and modified from the version in tools.namespace.  This
  version adds support for doing a 'dry run' that only describes what
  it would do, without changing anything.  It also adds a dest-path
  argument that can be used to move files to a completely different
  'root' path in a project, in addition to changing the namespace.

  Moves the .clj source file (found relative to source-path) for the
  namespace named old-sym to a file for a namespace named new-sym,
  relative to dest-path.

  WARNING: This function moves and deletes your source files!  Make
  sure you have a backup or version control."
  [old-sym new-sym source-path dest-path opts]
  (let [old-file (io/file source-path (#'move/ns-file-name old-sym))
        new-file (io/file dest-path (#'move/ns-file-name new-sym))]
    (when (:print? opts)
      (println (format "mkdir %s"
                       (abbreviate-path (str (.getParentFile new-file))
                                        dest-path (:dest-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (.mkdirs (.getParentFile new-file)))
    (when (:print? opts)
      (println (format "copy %s %s"
                       (abbreviate-path (str old-file)
                                        source-path (:source-path-abbrev opts))
                       (abbreviate-path (str new-file)
                                        dest-path (:dest-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (io/copy old-file new-file))
    (when (:print? opts)
      (println (format "delete %s"
                       (abbreviate-path (str old-file)
                                        source-path (:source-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (.delete old-file))
    (when (:print? opts)
      (println (format "delete old dir %s if empty"
                       (abbreviate-path (str (.getParentFile old-file))
                                        source-path (:source-path-abbrev opts)))))
    (when-not (:dry-run? opts)
      (loop [dir (.getParentFile old-file)]
        (when (empty? (.listFiles dir))
          (.delete dir)
          (recur (.getParentFile dir)))))))


(defn replace-ns-symbol
  "Copied and modified from the version in tools.namespace.  This
  version adds support for doing a 'dry run' that only describes what
  it would do, without changing anything.

  Given Clojure source as a string, replaces all occurrences of the
  namespace name old-sym with new-sym and returns a map containing:

    :new-source - modified source as a string

    :num-symbols - number of 'symbols' found in input string source

    :symbols - map of strings to integers, where the keys are
      'symbols' found in the input string source, and the values are
      the number of times that string was found in the input.  Can be
      useful for debugging the 'symbol' matching code.

    :num-replacements - number of times old-sym was found and replaced"
  [opts source old-sym new-sym]
  (let [old-name (name old-sym)
        new-name (name new-sym)
        num-symbols (atom 0)
        symbols-found (atom {})
        num-replacements (atom 0)
        ;; A lossless parser would be better, but this is adequate
        new-source (str/replace source @#'move/symbol-regex
                                (fn [match]
                                  (swap! num-symbols inc)
                                  (swap! symbols-found
                                         update-in [match] (fnil inc 0))
                                  (if (= match old-name)
                                    (do
                                      (swap! num-replacements inc)
                                      new-name)
                                    match)))]
    {:new-source new-source
     :num-symbols @num-symbols
     :symbols @symbols-found
     :num-replacements @num-replacements}))


(defn update-file
  "Copied and modified from the version in tools.namespace.  This
  version adds support for doing a 'dry run' that only describes what
  it would do, without modifying any files.

  Reads file as a string, calls f on opts, the string, plus any args.

  If opts has a logical false value for the key :dry-run? (or no such
  key), update-file then writes the value of f's :new-source key (f's
  return value being a map containing this key) as the new contents of
  file.  Avoids writing file if the content is unchanged."
  [file opts f & args]
  (let [old (slurp file)
        m (apply f opts old args)
        new (str (:new-source m))]
    (when-not (:dry-run? opts)
      (when-not (= old new)
        (spit file new)))
    m))


(comment
(use 'clojure.pprint)
(require '[dolly.clone :as c])
(def tns-path "/Users/jafinger/clj/dolly/copy-deps/tools.namespace/src/main/clojure")
(def staging-path "/Users/jafinger/clj/dolly/staging")
(def o1 (c/update-file (str tns-path "/clojure/tools/namespace.clj") {:dry-run? true} c/replace-ns-symbol 'clojure.tools.namespace 'eastwood.copieddeps.tns.clojure.tools.namespace))
(def m1 (into (sorted-map) (:symbols o1)))
(pprint m1)
)


(defn move-ns
  "Copied and modified from the version in tools.namespace.  This
  version adds support for doing a 'dry run' that only describes what
  it would do, without changing anything.  It also adds a dest-path
  argument that can be used to move files to a completely different
  'root' path in a project, in addition to changing the namespace.

  Moves the .clj source file (found relative to source-path) for the
  namespace named old-sym to a .clj source file (relative to
  dest-path) for the namespace named new-sym.  It also replaces all
  occurrences of the old name with the new name in all Clojure source
  files found in dirs.

  This is a purely textual transformation.  It does not work on
  namespaces require'd or use'd from a prefix list.

  WARNING: This function modifies and deletes your source files!  Make
  sure you have a backup or version control."
  [old-sym new-sym source-path dest-path dirs opts]
  ;; TBD: Add dry run option
  (move-ns-file old-sym new-sym source-path dest-path opts)
  (doseq [file (#'move/clojure-source-files dirs)]
    (update-file opts file replace-ns-symbol old-sym new-sym)))



;; TBD: Add function that repeats move-ns for multiple namespaces.
