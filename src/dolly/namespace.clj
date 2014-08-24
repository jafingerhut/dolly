(ns dolly.namespace
  (:require [clojure.data :as data]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dir :as dir]
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
  [fname]
  (let [^java.io.File f (if (instance? java.io.File fname)
                          fname
                          (java.io.File. ^String fname))]
    (.getCanonicalPath f)))


(defn clj-filename-to-ns
  [fname]
  (-> fname
      (str/replace-first #".clj$" "")
      (str/replace "_" "-")
      (str/replace File/separator ".")
      symbol))


(defn ns-to-clj-filename
  [namespace]
  (str (-> namespace
           str
           (str/replace "-" "_")
           (str/replace "." File/separator))
       ".clj"))


(defn filename-namespace-mismatches
  [dir-name-strs]
  (let [files-by-dir (into {} (for [dir-name-str dir-name-strs]
                                [dir-name-str (#'dir/find-files [dir-name-str])]))
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


(defn print-ns-deps-text
  ""
  [tracker dont-show]
  (let [nss-unload-order (:clojure.tools.namespace.track/unload tracker)
        nss-load-order (:clojure.tools.namespace.track/load tracker)
        dependencies (:dependencies (:clojure.tools.namespace.track/deps
                                     tracker))
        ns-number (atom 1)
        shown-namespaces (atom {})
        show (fn show [ns level]
               (when-not (dont-show ns)
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
                              (not (empty? (dependencies ns))))
                     (print (format "  [%d]" ns-already-printed-number)))
                   (println)
                   (when-not ns-already-printed-number
                     (doseq [child-ns (sort (dependencies ns))]
                       (show child-ns (inc level)))
                     (swap! shown-namespaces assoc ns next-ns-number)))))]
    (doseq [ns nss-unload-order]
      (when-not (@shown-namespaces ns)
        (show ns 0)))))


(defn nss-in-dirs
  [dir-name-strs opts]
  (let [dir-name-strs (map canonical-filename dir-name-strs)
        mismatches (filename-namespace-mismatches dir-name-strs)]
    (when (seq mismatches)
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
No other linting checks will be performed until these problems have
been corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions.")
      (flush)
      (System/exit 1)))
  (let [tracker (apply dir/scan-all (track/tracker) dir-name-strs)]
    ;; See doc/tools.namespace.md for some docs on the value returned
    ;; by dir/scan-all
    ;;(println "jafinger-dbg: tracker=")
    ;;(pp/pprint tracker)
    (when (:debug-desc opts)
      (println "Dependencies for namespaces in" (:debug-desc opts))
      (print-ns-deps-text tracker clojure-core-namespaces)
      (println "----------------------------------------"))
    (let [f1 (set (keys (:clojure.tools.namespace.file/filemap tracker)))
          f2 (:clojure.tools.namespace.dir/files tracker)]
      (when (not= f1 f2)
        (let [[only-in-filemap only-in-files] (data/diff f1 f2)]
          (when-not (empty? only-in-filemap)
            (println "\nFiles in /filemap but not in /files:")
            (pp/pprint only-in-filemap))
          (when-not (empty? only-in-files)
            (println "\nFiles in /files but not in /filemap, probably because they have no top-level ns form that tools.namespace can find:")
            (pp/pprint only-in-files)))))
    (:clojure.tools.namespace.track/load tracker)))
