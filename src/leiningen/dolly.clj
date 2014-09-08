(ns leiningen.dolly
  (:require [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [rhizome.dot :as dot]
            [dolly.namespace :as ns]))


(def dolly-version-string "0.1.0-SNAPSHOT")
(def dolly-url "https://github.com/jafingerhut/dolly")


(defn dolly-help [args]
  (println
"lein dolly: Clone Clojure library source code into your project.
Version" dolly-version-string "

    $ lein dolly <cmd>

where <cmd> is one of:

    help
    ns namespaces
    ls list-clones add add-clone remove remove-clone

See" dolly-url "for full documentation.")

  (when (not= args :short)
    ;; tbd: more help here, perhaps depending upon args
    )
  (flush))


(defn debug-dolly-args [project args opts namespaces]
  (println "project:")
  (pp/pprint project)
  (println "----------------------------------------")
  (println "args: ")
  (pp/pprint args)
  (println "(:source-paths project):")
  (pp/pprint (:source-paths project))
  (println "(:test-paths project):")
  (pp/pprint (:test-paths project))
  (println "opts:")
  (pp/pprint opts)
  (println "----------------------------------------")
  (println "namespaces:")
  (pp/pprint namespaces)
  (println "----------------------------------------")
  (flush))


(defn replace-paths-keywords
  [paths source-paths test-paths]
  (mapcat (fn [x]
            (if (keyword? x)
              (case x
                :source-paths source-paths
                :test-paths test-paths)
              [x]))
          paths))


(defn not-sequence [paths]
  (if (sequential? paths)
    nil
    {:err :paths-not-a-sequence
     :err-msg
     (with-out-str
       (println ":paths option value must be a sequence.  Found:" paths))}))


(defn unknown-paths-keywords
  [paths known-kws desc]
  (let [kw-set (set (filter keyword? paths))
        unknown-kws (set/difference kw-set known-kws)]
    (if (empty? unknown-kws)
      nil
      {:err :unknown-paths-keywords,
       :err-msg
       (with-out-str
         (println (format "The following keywords appeared in the paths specified after %s :"
                          desc))
         (println (format "    %s" (seq unknown-kws)))
         (println (format "The only keywords allowed in this list of paths are: %s"
                          (seq known-kws))))})))

(defn non-string-paths [paths]
  (let [non-strings (remove string? paths)]
    (if (empty? non-strings)
      nil
      {:err :non-string-paths
       :err-msg
       (with-out-str
         (println
"All :paths must be strings or keywords :source-paths or :test-paths
Found these non-strings:")
         (println (str (seq non-strings))))})))


(defn filename-exists? [filename]
  (.exists (io/file filename)))


(defn nonexistent-paths [paths]
  (let [nonexistent-paths (remove filename-exists? paths)]
    (if (empty? nonexistent-paths)
      nil
      {:err :nonexistent-paths
       :err-msg
       (with-out-str
         (println "All :paths must be files that exist.  Found these exceptions:")
         (println (str (seq nonexistent-paths))))})))


(defn handle-errwarn [info]
  (when (:warn info)
    (print (:warn-msg info))
    (flush))
  (when (:err info)
    (print (:err-msg info))
    (flush)
    (System/exit 1)))


(defn calc-ns-opts
  [project cmd-opts]
  (let [paths (or (:paths cmd-opts) [:source-paths])]
    (or
     (not-sequence paths)
     (unknown-paths-keywords paths #{:source-paths :test-paths}
                             ":paths")
     (let [paths (replace-paths-keywords paths (:source-paths project)
                                         (:test-paths project))]
       (or
        (non-string-paths paths)
        (nonexistent-paths paths)
        (merge {:namespace-show-opts [{:exclude ns/clojure-core-namespaces}]}
               cmd-opts
               {:err nil, :paths paths}))))))


(defn dolly-ns
  [project cmdline-args]
  (let [cmd-opts (if-let [s (first cmdline-args)]
                   (edn/read-string s)
                   {})
        ns-opts (calc-ns-opts project cmd-opts)
        _ (handle-errwarn ns-opts)
        ns-info (ns/namespaces-in-dirs ns-opts)
        _ (handle-errwarn ns-info)
;;        _ (println "jafinger-dbg: ns-info")
;;        _ (pp/pprint ns-info)
        graph-args (ns/ns-info->graph-args ns-info)]
    (if (contains? #{nil :text :dot} (:format ns-info))
      ;; Then don't require rhizome.viz, since by default it pops
      ;; up another JVM GUI-related icon in the Dock on Mac OS X.
      (case (:format ns-info)
        (nil :text) (do
                      (println "Dependencies:")
                      (ns/print-ns-deps-text ns-info))
        :dot (let [dot-fname "nsdeps.dot"]
               (spit dot-fname (apply dot/graph->dot graph-args))
               (println "Wrote file" dot-fname)))
      (do
        (require 'rhizome.viz)
        (let [graph->svg (ns-resolve 'rhizome.viz 'graph->svg)
              graph->image (ns-resolve 'rhizome.viz 'graph->image)
              save-image (ns-resolve 'rhizome.viz 'save-image)
              view-graph (ns-resolve 'rhizome.viz 'view-graph)]
          (try
            (case (:format ns-info)
              :svg (let [svg-fname "nsdeps.svg"]
                     (spit svg-fname (apply graph->svg graph-args))
                     (println "Wrote file" svg-fname))
              :png (let [png-fname "nsdeps.png"]
                     (save-image (apply graph->image graph-args) png-fname)
                     (println "Wrote file" png-fname))
              :window (do
                        (apply view-graph graph-args)
                        ;; TBD: If I don't do something to delay the
                        ;; program exiting here, the window is shown
                        ;; but then is closed immediately.  Is there a
                        ;; way to make this program end only when
                        ;; someone closes the new window?
                        (println "Delaying quitting until you press return, otherwise the new window will close:")
                        (read-line)))
            (catch java.io.IOException e
              (let [msg (.getMessage e)]
                (if (re-find #"Cannot run program \"dot\".* No such file" msg)
                  (binding [*out* *err*]
                    (println (format "Could not find program 'dot'.
Output format %s requires installing Graphviz (http://www.graphviz.org)"
                                     (:format ns-info))))
                  (throw e))))))))))


(defn dolly
  "Clone Clojure lib source into your project."
  [project & all-dolly-args]
  (let [[dolly-cmd & args] all-dolly-args]
    ;;(debug-dolly-args project all-dolly-args)
    (case dolly-cmd
      nil
      (dolly-help :short)
      
      ("help")
      (dolly-help args)
      
      ("ns" "namespaces")
      (dolly-ns project args)
      
      ("ls" "list-clones")
      (println "list-clones not implemented yet")
      
      ("add" "add-clone")
      (println "add-clone not implemented yet")
              
      ("remove" "remove-clone")
      (println "remove-clone not implemented yet")
      
      (println (format "Unknown dolly command '%s'" dolly-cmd)))))
