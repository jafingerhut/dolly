(ns dolly.namespace
  (:require [clojure.data :as data]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.dependency :as dep]
            [dolly.util :as util])
  (:import [java.io File]))


;; TBD: Is there a way we can get these by iterating through some list
;; of namespaces and restrict it to only those in Clojure itself?

(def clojure-core-namespaces
  '#{
     clojure.core
     clojure.core.protocols
     clojure.data
     clojure.edn
     clojure.inspector
     clojure.instant
     clojure.java.browse
     clojure.java.browse-ui
     clojure.java.io
     clojure.java.javadoc
     clojure.java.shell
     clojure.main
     clojure.pprint
     clojure.reflect
     clojure.repl
     clojure.set
     clojure.stacktrace
     clojure.string
     clojure.template
     clojure.test
     clojure.test.junit
     clojure.test.tap
     clojure.uuid
     clojure.walk
     clojure.xml
     clojure.zip
     })


(defn canonical-filename
  "Returns the canonical file name for the given file name.  A
canonical file name is platform dependent, but is both absolute and
unique.  See the Java docs for getCanonicalPath for some more details,
and the examples below.

    http://docs.oracle.com/javase/7/docs/api/java/io/File.html#getCanonicalPath%28%29

Examples:

Context: A Linux or Mac OS X system, where the current working
directory is /Users/jafinger/clj/dolly

user=> (ns/canonical-filename \"README.md\")
\"/Users/jafinger/clj/dolly/README.md\"

user=> (ns/canonical-filename \"../../Documents/\")
\"/Users/jafinger/Documents\"

user=> (ns/canonical-filename \"../.././clj/../Documents/././\")
\"/Users/jafinger/Documents\"

Context: A Windows 7 system, where the current working directory is
C:\\Users\\jafinger\\clj\\dolly

user=> (ns/canonical-filename \"README.md\")
\"C:\\Users\\jafinger\\clj\\dolly\\README.md\"

user=> (ns/canonical-filename \"..\\..\\Documents\\\")
\"C:\\Users\\jafinger\\Documents\"

user=> (ns/canonical-filename \"..\\..\\.\\clj\\..\\Documents\\.\\.\\\")
\"C:\\Users\\jafinger\\Documents\""
  [fname]
  (let [^java.io.File f (if (instance? java.io.File fname)
                          fname
                          (java.io.File. ^String fname))]
    (.getCanonicalPath f)))


(defn clj-filename-to-ns
  "Perform the opposite transformation as ns-to-clj-filename,
i.e. from a string representing a Clojure filename to a symbol of its
namespace name.  Underscores are converted to dashes, and
platform-specific path separators to dots.

Examples:

Context: A Linux or Mac OS X system

user=> (clj-filename-to-ns \"com/example/my_ns.clj\")
com.example.my-ns

Context: A Windows 7 system

user=> (clj-filename-to-ns \"com\\example\\my_ns.clj\")
com.example.my-ns

;; You probably don't ever want to give a file name like this on a
;; Windows system.
user=> (clj-filename-to-ns \"com/example/my_ns.clj\")
com/examples/my-ns"
  [fname]
  (-> fname
      (str/replace-first #".clj$" "")
      (str/replace "_" "-")
      (str/replace File/separator ".")
      symbol))


(defn ns-to-clj-filename
  "Take a symbol or other stringable thing that produces a string that
looks like a namespace.  Convert it to a Clojure source file name.
Dashes converted to underscores, and dots to a platform-specific path
separator.

Examples:

Context: A Linux or Mac OS X system

user=> (ns-to-clj-filename 'com.example.my-ns)
\"com/example/my_ns.clj\"

Context: A Windows 7 system

user=> (ns-to-clj-filename 'com.example.my-ns)
\"com\\example\\my_ns.clj\""
  [namespace]
  (str (-> namespace
           str
           (str/replace "-" "_")
           (str/replace "." File/separator))
       ".clj"))


(defn filename-namespace-mismatches
  [paths]
  (let [files-by-dir (into {} (for [path paths]
                                [path (#'dir/find-files [path])]))
        fd-by-dir (util/map-vals (fn [files]
                                   (#'file/files-and-deps files))
                                 files-by-dir)]
    (into
     {}
     (for [[dir fd] fd-by-dir,
           [f namespace] (:filemap fd)
           :let [fname (str f)
                 fname (if (.startsWith fname dir)
                         (subs fname (inc (count dir))) ; inc to get rid of a separator
                         fname)
                 desired-ns (clj-filename-to-ns fname)
                 desired-fname (ns-to-clj-filename namespace)]
           :when (not= fname desired-fname)]
       [fname {:dir dir, :namespace namespace,
               :recommended-fname desired-fname,
               :recommended-namespace desired-ns}]))))


(defn ns-matches? [ns-sym ns-spec]
  ;; TBD: Later allow ns-spec to have wildcards or maybe a regex pattern
  (= ns-sym ns-spec))


(defn includes-namespace? [ns-sym coll]
  (some #(ns-matches? ns-sym %) coll))


(def first-time (atom true))

(defn should-show-namespace? [ns-sym show-opts]
;;  (when @first-time
;;    (println (format "jafinger-dbg: show-opts='%s'" show-opts))
;;    (reset! first-time false))
  (loop [opts show-opts]
    (when-first [opt opts]
      (cond
       (contains? opt :include) (includes-namespace? ns-sym (:include opt))
       (contains? opt :exclude) (not (includes-namespace? ns-sym (:exclude opt)))
       :else (recur (next opts))))))


(defn print-ns-deps-text
  ""
  [ns-info]
  (let [show-opts (:namespace-show-opts ns-info)
        nss-unload-order (:clojure.tools.namespace.track/unload ns-info)
        dependencies (:dependencies (:clojure.tools.namespace.track/deps
                                     ns-info))
        ns-number (atom 1)
        shown-namespaces (atom {})
        namespace-showed-children (atom #{})
        show (fn show [ns level parent-ns]
               (when (should-show-namespace? ns show-opts)
                 (when parent-ns
                   (swap! namespace-showed-children conj parent-ns))
                 (let [ns-already-printed-number (@shown-namespaces ns)
                       next-ns-number @ns-number]
                   (if ns-already-printed-number
                     (print "   ")
                     (do
                       (print (format "%3d" next-ns-number))
                       (swap! ns-number inc)))
                   (print " ")
                   (dotimes [i level] (print "  "))
                   (print ns)
                   (when (and ns-already-printed-number
                              (contains? @namespace-showed-children ns))
                     (print (format "  [%d]" ns-already-printed-number)))
                   (println)
                   (when-not ns-already-printed-number
                     (doseq [child-ns (sort (dependencies ns))]
                       (show child-ns (inc level) ns))
                     (swap! shown-namespaces assoc ns next-ns-number)))))]
    (doseq [ns nss-unload-order]
      (when-not (@shown-namespaces ns)
        (show ns 0 nil)))))


(defn ns-info->graph-args
  "ns-info should be a map containing at least the following keys:

  :namespace-show-opts

The following two are from a tools.namespace 'tracker' map:

  :clojure.tools.namespace.track/unload
  :clojure.tools.namespace.track/deps

Returns a sequence of arguments to be passed to any of the graph->*
functions from the rhizome library, e.g. graph->dot, graph->svg,
graph->image."
  [ns-info]
  (let [show-opts (:namespace-show-opts ns-info)
        nss-unload-order (:clojure.tools.namespace.track/unload ns-info)
;;        _ (println "jafinger-dbg: ns-info->graph-args nss-unload-order=" (seq nss-unload-order))
        nss-set (set nss-unload-order)
        deps (:clojure.tools.namespace.track/deps ns-info)
        dependencies (:dependencies deps)
        nodes (set/union
               nss-set
               (set (filter #(should-show-namespace? % show-opts)
                            (dep/transitive-dependencies-set deps nss-set))))
;;        _ (println "jafinger-dbg: ns-info->graph-args nodes=" (seq nodes))
        ]
    [nodes dependencies :node->descriptor (fn [n] {:label n})]))


(defn filename-namespace-mismatch-map [mismatches]
  {:err :filename-namespace-mismatch
   :err-data mismatches
   :err-msg
   (with-out-str
     (println "The following file(s) contain ns forms with namespaces that do not correspond
with their file names:")
     (doseq [[fname {:keys [dir namespace recommended-fname recommended-namespace]}]
             mismatches]
       (println (format "Directory: %s" dir))
       (println (format "    File                 : %s" fname))
       (println (format "    has namespace        : %s" namespace))
       (println (format "    should have namespace: %s" recommended-namespace))
       (println (format "    or should be in file : %s" recommended-fname)))
     (println "
No other operations will be performed until these problems have been
corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions."))})


(defn before?
  "True if x comes before y in an ordered collection.  Also returns
true if only x is in the collection, or neither x nor y is in the
collection.  Returns false only if y is found before x."
  [coll x y]
  (loop [[item & more] (seq coll)]
    (cond (nil? item) true  ; end of the seq
          (= x item) true  ; x comes first
          (= y item) false
          :else (recur more))))


(defn tracker-dependency-pairs
  "From :dependencies in the tracker, return a sequence of 2-element
vectors [a b], where a and b are symbols representing namespaces, and
a requires or uses b."
  [tracker]
  (let [dependencies (:dependencies (:clojure.tools.namespace.track/deps
                                     tracker))]
    (for [a (keys dependencies)
          b (dependencies a)]
      [a b])))


(defn tracker-dependent-pairs
  "From :dependents in the tracker, return a sequence of 2-element
vectors [a b], where a and b are symbols representing namespaces, and
a requires or uses b."
  [tracker]
  (let [dependents (:dependents (:clojure.tools.namespace.track/deps
                                 tracker))]
    (for [b (keys dependents)
          a (dependents b)]
      [a b])))


(defn wrong-tracker-load-unload-order
  "Returns nil if the tracker has load and unload orders that are
consistent with its :dependencies and :dependents graphs.  If there
are inconsistencies, returns a map containing either or both of the
keys :load or :unload, where the values are a collection of 2-element
vectors [a b] where a and b are symbols representing namespace names,
and a requires or uses b, and that dependency order is violated by the
load or unload order in the tracker."
  [tracker]
  (let [load-order (:clojure.tools.namespace.track/load tracker)
        load-set (set load-order)
        unload-order (:clojure.tools.namespace.track/unload tracker)
        unload-set (set unload-order)
        dependency-pairs (tracker-dependency-pairs tracker)
        dependent-pairs (tracker-dependent-pairs tracker)
        load-order-violations (set
                               (filter
                                (fn [[a b]]
                                  (and (load-set a) (load-set b)
                                       (before? load-order a b)))
                                (concat dependency-pairs dependent-pairs)))
        unload-order-violations (set
                                 (filter
                                  (fn [[a b]]
                                    (and (unload-set a) (unload-set b)
                                         (before? unload-order b a)))
                                  (concat dependency-pairs dependent-pairs)))]
    (if (or (seq load-order-violations)
            (seq unload-order-violations))
      {:load (seq load-order-violations)
       :unload (seq unload-order-violations)}
      nil)))


(defn bad-load-unload-order-map [bad-order tracker]
  {:err :bad-load-unload-order
   :err-data bad-order
   :err-msg
   (with-out-str
     (when (:load bad-order)
       (println "Namespace tracker load order is:")
       (pp/pprint (:clojure.tools.namespace.track/load tracker))
       (println "\nIt violates the following [a b] dependencies, where a requires or uses b:")
       (pp/pprint (:load bad-order)))
     (when (:unload bad-order)
       (println "Namespace tracker unload order is:")
       (pp/pprint (:clojure.tools.namespace.track/unload tracker))
       (println "\nIt violates the following [a b] dependencies, where a requires or uses b:")
       (pp/pprint (:unload bad-order)))
     (println "
This is most likely a bug in tools.namespace, or in the way that Dolly
uses tools.namespace.  Please report this as an issue for Dolly at:

    https://github.com/jafingerhut/dolly/issues"))})


(defn namespaces-in-dirs [opts]
  (let [paths (map canonical-filename (:paths opts))
        mismatches (filename-namespace-mismatches paths)]
    (if (seq mismatches)
      (filename-namespace-mismatch-map mismatches)
      (let [tracker (apply dir/scan-all (track/tracker) paths)]
        ;; See doc/tools.namespace.md for some docs on the value returned
        ;; by dir/scan-all
        ;;(println "jafinger-dbg: tracker=")
        ;;(pp/pprint tracker)
        (let [f1 (set (keys (:clojure.tools.namespace.file/filemap tracker)))
              f2 (:clojure.tools.namespace.dir/files tracker)]
          (merge
           opts
           (if-let [bad-order (wrong-tracker-load-unload-order tracker)]
             (bad-load-unload-order-map bad-order tracker)
             {:err nil})
           (if (= f1 f2)
             {:warn nil}
             (let [[only-in-filemap only-in-files] (data/diff f1 f2)]
               {:warn :filemap-files-difference
                :warn-data {:in-filemap-not-files only-in-filemap
                            :in-files-not-filemap only-in-files}
                :warn-msg
                (with-out-str
                  (when-not (empty? only-in-filemap)
                    (println "\nFiles in /filemap but not in /files:")
                    (pp/pprint only-in-filemap))
                  (when-not (empty? only-in-files)
                    (println
"\nFiles in /files but not in /filemap, probably because they have
no top-level ns form that tools.namespace can find:")
                    (pp/pprint (map str only-in-files))))}))
           tracker))))))
