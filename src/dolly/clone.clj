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


(defn distinct-parent-dirs
  "Given a collection 's' of java.io.File objects, return a lazy
sequence of the parent directories of those files as returned by
method .getParentFile, with at most one occurrence of each parent
directory."
  [s]
  (distinct
   (map (fn [^File f] (.getParentFile f)) s)))


(defn copy-ns-files
  "Copies the .clj source files (found relative to 'source-path') for
the namespaces named by the symbols in collection 'ns-syms' to a file
for the same namespace, but relative to 'dest-path'.

Map 'opts' can be used to supply options.  If key :dry-run? is present
and has a logical true value, then no modifications will be made to
any files.  If key :print? is present and has a logical true value,
messages will be printed to *out* describing what is being done (or
if :dry-run? is true, then what would have been done).

WARNING: This function can overwrite any source file that was already
at the destination!  Make sure you have a backup or version control."
  [source-path dest-path ns-syms opts]
  (let [ns-fnames (map #'move/ns-file-name ns-syms)
        old-new-file-pairs (map (fn [ns-fname]
                                  [ (io/file source-path ns-fname)
                                    (io/file dest-path ns-fname) ])
                                ns-fnames)
        new-dirs (distinct-parent-dirs (map second old-new-file-pairs))]
    (doseq [new-dir new-dirs]
      (when (:print? opts)
        (println (format "mkdir %s"
                         (abbreviate-path (str new-dir)
                                          dest-path (:dest-path-abbrev opts)))))
      (when-not (:dry-run? opts)
        (.mkdirs ^File new-dir)))
    (doseq [[old-file new-file] old-new-file-pairs]
      (when (:print? opts)
        (println (format "copy %s %s"
                         (abbreviate-path (str old-file) source-path
                                          (:source-path-abbrev opts))
                         (abbreviate-path (str new-file) dest-path
                                          (:dest-path-abbrev opts)))))
      (when-not (:dry-run? opts)
        (io/copy old-file new-file)))))


(defn namespace-extends? [ns root-ns]
  (or (= ns root-ns)
      (.startsWith (str ns) (str root-ns "."))))


(defn copy-namespaces-unmodified
  "Scan through all files in directory source-path or one of its
subdirectories.  For every .clj source file that contains an ns
declaration with a namespace that is exactly ns-sym, or begins with
ns-sym followed by a dot, copy it unmodified to a file that is rooted
at the directory dest-path, and has the same relative path name after
dest-path that the original does after source-path.

The namespaces will first be checked that they correspond with the
file names in which they occur.  If they do not, an error is returned
and no files are copied.

For example, if source-path is /source/path and it contains a file
/source/path/one/two.clj, it will be an error if the namespace inside
is anything other than 'one.two'."
  [source-path dest-path ns-sym opts]
  (let [ns-info (ns/namespaces-in-dirs {:paths [source-path]})]
    (if (:err ns-info)
      ns-info
      (let [all-nss (map second (:clojure.tools.namespace.file/filemap ns-info))
            to-copy? #(namespace-extends? % ns-sym)
            do-copy (filter to-copy? all-nss)
            dont-copy (remove to-copy? all-nss)]
        (when (:print? opts)
          (println (format "Copy Clojure source files in namespace %s unmodified\n  from source path S='%s'\n  to dest path D='%s'"
                           ns-sym source-path dest-path)))
        (copy-ns-files source-path dest-path do-copy
                       (merge opts {:source-path-abbrev "S"
                                    :dest-path-abbrev "D"}))
        (when (and (:print? opts) (seq dont-copy))
          (println (format "Don't copy following namespaces from S since they are not a 'sub-namespace' of %s"
                           ns-sym))
          (doseq [ns dont-copy]
            (println (format "  %s" ns))))))))


(comment
(require '[dolly.clone :as c])
(def tns-path "/Users/jafinger/clj/dolly/copy-deps/tools.namespace/src/main/clojure")
(def staging-path "/Users/jafinger/clj/dolly/staging")
(def o1 (c/copy-namespaces-unmodified tns-path staging-path 'clojure.tools.namespace {:dry-run? true :print? true}))
;;(print (apply str (:dry-run-msgs o1)))
(def o2 (c/copy-namespaces-unmodified tns-path staging-path 'clojure.tools.namespace.dir {:dry-run? true :print? true}))
;;(print (apply str (:dry-run-msgs o2)))
)


(defn move-ns-files
  "Modified version of tools.namespace's move-ns-file.

Moves the .clj source files (found relative to 'source-path') for the
namespaces named as the symbols that are keys in map 'sym-map' to
files for the namespaces named as the values in 'sym-map', relative to
'dest-path'.

Map 'opts' can be used to supply options.  If key :dry-run? is present
and has a logical true value, then no modifications will be made to
any files.  If key :print? is present and has a logical true value,
messages will be printed to *out* describing what is being done (or
if :dry-run? is true, then what would have been done).

WARNING: This function moves and deletes your source files!  Make sure
you have a backup or version control."
  [source-path dest-path sym-map opts]
  (let [inf
        (into {}
              (for [[old-sym new-sym] sym-map]
                [old-sym {:new-sym new-sym
                          :old-file (io/file source-path
                                             (#'move/ns-file-name old-sym))
                          :new-file (io/file dest-path
                                             (#'move/ns-file-name new-sym))}]))
        new-dirs (distinct-parent-dirs (map #(:new-file (val %)) inf))
        old-dirs (distinct-parent-dirs (map #(:old-file (val %)) inf))]
    (doseq [new-dir new-dirs]
      (when (:print? opts)
        (println (format "mkdir %s"
                         (abbreviate-path (str new-dir)
                                          dest-path (:dest-path-abbrev opts)))))
      (when-not (:dry-run? opts)
        (.mkdirs ^File new-dir)))
    (doseq [[old-sym {:keys [old-file new-file]}] inf]
      (when (:print? opts)
        (println (format "copy %s %s"
                         (abbreviate-path (str old-file) source-path
                                          (:source-path-abbrev opts))
                         (abbreviate-path (str new-file) dest-path
                                          (:dest-path-abbrev opts))))
        (println (format "delete %s"
                         (abbreviate-path (str old-file) source-path
                                          (:source-path-abbrev opts)))))
      (when-not (:dry-run? opts)
        (io/copy old-file new-file)
        (.delete ^File old-file)))
    (doseq [old-dir old-dirs]
      (when (:print? opts)
        (println (format "delete old dir %s if empty"
                         (abbreviate-path (str old-dir) source-path
                                          (:source-path-abbrev opts)))))
      (when-not (:dry-run? opts)
        (loop [^File dir old-dir]
          (when (empty? (.listFiles dir))
            (.delete dir)
            (recur (.getParentFile dir))))))))


(defn replace-ns-symbols
  "Modified version of tools.namespace's replace-ns-symbol.

Given Clojure source as a string 'source', replaces all occurrences of
the namespace names that are keys of sym-map with the corresponding
values of those keys.  Returns a map containing:

  :new-source - modified source as a string

  :num-symbols - number of 'symbols' found in input string source

  :symbols - map of strings to integers, where the keys are 'symbols'
    found in the input string source, and the values are the number of
    times that string was found in the input.  Can be useful for
    debugging the 'symbol' matching code.

  :symbols-replaced - map where the keys are string-ified versions of
    the keys in sym-map that were found in the string 'source', and
    the values are the number of times that string was found and
    replaced."
  [opts source sym-map]
  (let [string-map (into {} (for [[k v] sym-map] [(str k) (str v)]))
        num-symbols (atom 0)
        symbols-found (atom {})
        symbols-replaced (atom {})
        ;; A lossless parser would be better, but this is adequate
        new-source (str/replace source @#'move/symbol-regex
                                (fn [match]
                                  (swap! num-symbols inc)
                                  (swap! symbols-found
                                         update-in [match] (fnil inc 0))
                                  (if (contains? string-map match)
                                    (do
                                      (swap! symbols-replaced
                                             update-in [match] (fnil inc 0))
                                      (get string-map match))
                                    match)))]
    {:new-source new-source
     :num-symbols @num-symbols
     :symbols @symbols-found
     :symbols-replaced @symbols-replaced}))


(defn update-file
  "Modified version of tools.namespace's update-file.

Reads file as a string, calls f on opts, the string, plus any args.
update-file returns the same value that f returned.

If map 'opts' has a logical true value for the key :dry-run?, no files
are modified.  If it is not present or has a logical false value,
update-file writes the value of f's :new-source key (f's return value
being a map containing this key) as the new contents of file.  Avoids
writing file if the content is unchanged."
  [file opts f & args]
  (let [old (slurp file)
        m (apply f opts old args)
        new (str (:new-source m))]
    (when-not (:dry-run? opts)
      (when-not (= old new)
        (spit file new)))
    m))


(defn move-namespaces
  "Modified version of tools.namespace's move-ns.

Moves the .clj source files (found relative to 'source-path') for the
namespaces named as the symbols that are keys in 'sym-map' to files
for the namespaces named as the values in 'sym-map', relative to
'dest-path'.

It also replaces all occurrences of the old namespaces with the
corresponding new namespaces in all Clojure source files found in
'dirs'.  This is a purely textual transformation.  It does not work on
namespaces require'd or use'd from a prefix list.

Map 'opts' can be used to supply options.  If key :dry-run? is present
and has a logical true value, then no modifications will be made to
any files.  If key :print? is present and has a logical true value,
messages will be printed to *out* describing what is being done (or
if :dry-run? is true, then what would have been done).

WARNING: This function modifies and deletes your source files!  Make
sure you have a backup or version control."
  [source-path dest-path sym-map dirs opts]
  (move-ns-files source-path dest-path sym-map opts)
  (doseq [file (#'move/clojure-source-files dirs)]
    (let [m (update-file file opts replace-ns-symbols sym-map)]
      (when (:print? opts)
        (println (format "File %s" (str file)))
        (if (empty? (:symbols-replaced m))
          (println (format "  No symbols replaced"))
          (doseq [sym (sort (keys (:symbols-replaced m)))]
            (println (format "  %3d instances of %s replaced"
                             (-> m :symbols-replaced (get sym)) sym))))))))


(defn move-namespaces-and-rename
  "Scan through all files in directory 'source-path' or one of its
subdirectories.  For every .clj source file that contains an ns
declaration with a namespace that is exactly 'source-ns-sym', or
begins with 'source-ns-sym' followed by a dot, move it to a file that
is rooted at the directory 'dest-path', and has its namespace changed
so that the prefix that was 'source-ns-sym' is replaced with
'dest-ns-sym'.

It also replaces all occurrences of the source namespaces with the
corresponding destination namespaces in all Clojure source files found
in 'dirs'.  This is a purely textual transformation.  It does not work
on namespaces require'd or use'd from a prefix list.

The source namespaces will first be checked that they correspond with
the file names in which they occur.  If they do not, an error is
returned and no files are copied.

For example, if 'source-path' is /source/path and it contains a file
/source/path/one/two.clj, it will be an error if the namespace inside
is anything other than 'one.two'.

Map 'opts' can be used to supply options.  If key :dry-run? is present
and has a logical true value, then no modifications will be made to
any files.  If key :print? is present and has a logical true value,
messages will be printed to *out* describing what is being done (or
if :dry-run? is true, then what would have been done).

WARNING: This function moves and deletes your source files!  Make sure
you have a backup or version control."
  [source-path dest-path source-ns-sym dest-ns-sym dirs opts]
  (let [ns-info (ns/namespaces-in-dirs {:paths [source-path]})]
    (if (:err ns-info)
      ns-info
      (let [all-nss (map second (:clojure.tools.namespace.file/filemap ns-info))
            to-move? #(namespace-extends? % source-ns-sym)
            do-move (filter to-move? all-nss)
            dont-move (remove to-move? all-nss)
            source-ns-sym-len (count (str source-ns-sym))
            sym-map (into {}
                          (for [ns do-move]
                            [ns (symbol (str
                                         dest-ns-sym
                                         (subs (str ns) source-ns-sym-len)))]))]
        (when (:print? opts)
          (println (format "Move Clojure source files in namespace %s\n  from source path S='%s'\n  to dest path D='%s'\n  with new 'root' namespace %s"
                           source-ns-sym source-path dest-path dest-ns-sym)))
        (move-namespaces source-path dest-path sym-map dirs opts)
        (when (and (:print? opts) (seq dont-move))
          (println (format "Don't move following namespaces from S since they are not a 'sub-namespace' of %s"
                           source-ns-sym))
          (doseq [ns dont-move]
            (println (format "  %s" ns))))))))


(comment
(use 'clojure.pprint)
(require '[dolly.clone :as c])
(def tns-path "/Users/jafinger/clj/dolly/copy-deps/tools.namespace/src/main/clojure")
(def staging-path "/Users/jafinger/clj/dolly/staging")
(def dolly-src-path ["/Users/jafinger/clj/dolly/src" tns-path])
(def o1 (c/update-file (str tns-path "/clojure/tools/namespace.clj") {:dry-run? true} c/replace-ns-symbols '{clojure.tools.namespace eastwood.copieddeps.tns.clojure.tools.namespace}))
(def m1 (into (sorted-map) (:symbols o1)))
(pprint m1)

(def sym-map '{clojure.tools.namespace eastwood.copieddeps.tns.clojure.tools.namespace, clojure.tools.namespace.dependency eastwood.copieddeps.tns.clojure.tools.namespace.dependency, clojure.tools.namespace.dir eastwood.copieddeps.tns.clojure.tools.namespace.dir, clojure.tools.namespace.file eastwood.copieddeps.tns.clojure.tools.namespace.file, clojure.tools.namespace.find eastwood.copieddeps.tns.clojure.tools.namespace.find, clojure.tools.namespace.move eastwood.copieddeps.tns.clojure.tools.namespace.move, clojure.tools.namespace.parse eastwood.copieddeps.tns.clojure.tools.namespace.parse, clojure.tools.namespace.reload eastwood.copieddeps.tns.clojure.tools.namespace.reload, clojure.tools.namespace.repl eastwood.copieddeps.tns.clojure.tools.namespace.repl, clojure.tools.namespace.track eastwood.copieddeps.tns.clojure.tools.namespace.track})
(c/move-namespaces tns-path staging-path sym-map dolly-src-path {:dry-run? true :print? true})

(c/move-namespaces-and-rename tns-path staging-path 'clojure.tools.namespace 'eastwood.copieddeps.tns.clojure.tools.namespace dolly-src-path {:dry-run? true :print? true})

(c/move-namespaces-and-rename tns-path staging-path 'clojure.tools.namespace.track 'eastwood.copieddeps.tns.clojure.tools.namespace.track dolly-src-path {:dry-run? true :print? true})
)
