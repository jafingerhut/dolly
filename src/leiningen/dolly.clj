(ns leiningen.dolly
  (:require [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.java.io :as io]
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
        (assoc cmd-opts
          :err nil
          :paths paths
          :namespace-show-opts [{:exclude ns/clojure-core-namespaces}]))))))


(defn handle-errwarn [info]
  (when (:err info)
    (print (:err-msg info))
    (flush)
    (System/exit 1))
  (when (:warn info)
    (print (:warn-msg info))
    (flush)))


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
      (let [cmd-opts (if-let [s (first args)]
                       (edn/read-string s)
                       {})
            ns-opts (calc-ns-opts project cmd-opts)
            _ (handle-errwarn ns-opts)
            ns-info (ns/namespaces-in-dirs ns-opts)]
        (handle-errwarn ns-info)
        (println "Dependencies:")
        (ns/print-ns-deps-text ns-info))
      
      ("ls" "list-clones")
      (println "list-clones not implemented yet")
      
      ("add" "add-clone")
      (println "add-clone not implemented yet")
              
      ("remove" "remove-clone")
      (println "remove-clone not implemented yet")
      
      (println (format "Unknown dolly command '%s'" dolly-cmd)))))
