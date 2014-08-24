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
  (when @first-time
    (println (format "jafinger-dbg: show-opts='%s'" show-opts))
    (reset! first-time false))
  ;; TBD
  true
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
        nss-load-order (:clojure.tools.namespace.track/load ns-info)
        dependencies (:dependencies (:clojure.tools.namespace.track/deps
                                     ns-info))
        ns-number (atom 1)
        shown-namespaces (atom {})
        show (fn show [ns level]
               (when (should-show-namespace? ns show-opts)
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
No other linting checks will be performed until these problems have
been corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions."))})


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
           {:err nil}
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
                    (println "\nFiles in /files but not in /filemap, probably because they have no top-level ns form that tools.namespace can find:")
                    (pp/pprint only-in-files)))}))
           tracker))))))
