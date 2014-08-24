(ns leiningen.dolly
  (:require [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [dolly.namespace :as ns]))


(def dolly-version-string "0.1.0-SNAPSHOT")


(defn dolly-help []
  (println "lein dolly: Clone Clojure library source code into your project.")
  (println (format "Version %s" dolly-version-string))
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


(defn replace-ns-keywords
  [namespaces source-paths test-paths]
  (mapcat (fn [x]
            (if (keyword? x)
              (case x
                :source-paths source-paths
                :test-paths test-paths
                ;;:force-order []
                )
              [x]))
          namespaces))


(defn unknown-ns-keywords
  [namespaces known-ns-keywords desc]
  (let [keyword-set (set (filter keyword? namespaces))
        unkn-ns-keywords (set/difference keyword-set known-ns-keywords)]
    (if (empty? unkn-ns-keywords)
      nil
      {:err :unknown-ns-keywords,
       :msg
       (with-out-str
         (println (format "The following keywords appeared in the namespaces specified after %s :"
                          desc))
         (println (format "    %s" (seq unkn-ns-keywords)))
         (println (format "The only keywords allowed in this list of namespaces are: %s"
                          (seq known-ns-keywords))))})))


(defn opts->namespaces
  [opts]
  (let [namespaces (distinct (or (:namespaces opts)
                                 [:source-paths :test-paths]))
        excluded-namespaces (set (:exclude-namespaces opts))]
    ;; Return an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (or
     (unknown-ns-keywords namespaces #{:source-paths :test-paths}
                          ":namespaces")
     (unknown-ns-keywords excluded-namespaces #{:source-paths :test-paths}
                          ":exclude-namespaces")
     ;; If keyword :source-paths occurs in namespaces or
     ;; excluded-namespaces, replace it with all namespaces found in
     ;; the directories in (:source-paths opts), in an order that
     ;; honors dependencies, and similarly for :test-paths.
     ;; namespaces-in-dirs traverses part of the file system, so only call it
     ;; once for each of :source-paths and :test-paths, and only if
     ;; needed.
     (let [source-paths (if (some #(= % :source-paths)
                                  (concat namespaces excluded-namespaces))
                          (ns/namespaces-in-dirs
                           (:source-paths opts)
                           {:debug-desc (str ":source-paths "
                                             (seq (:source-paths opts)))}))
           test-paths (if (some #(= % :test-paths)
                                (concat namespaces excluded-namespaces))
                        (ns/namespaces-in-dirs
                         (:test-paths opts)
                         {:debug-desc (str ":test-paths "
                                           (seq (:test-paths opts)))}))
           namespaces (replace-ns-keywords namespaces source-paths test-paths)
           namespaces (distinct namespaces)
           excluded-namespaces (set (replace-ns-keywords excluded-namespaces
                                                         source-paths
                                                         test-paths))
           namespaces (remove excluded-namespaces namespaces)]
       {:err nil, :namespaces namespaces}))))


(defn dolly
  "Clone Clojure lib source into your project."
  [project & args]
  ;;(debug-dolly-args project args)
  (if (= (first args) "help")
    (dolly-help)
    (let [opts (merge (select-keys project [:source-paths :test-paths])
                      (if (>= (count args) 1)
                        (edn/read-string (first args))
                        {}))
          ret (opts->namespaces opts)
          _ (do
              (println "jafinger-dbg: opts->namespaces ret=")
              (pp/pprint ret)
              (println "----------------------------------------")
              )
          {:keys [err msg namespaces]} ret]
      ;;(debug-dolly-args project args opts namespaces)
      (when err
        (print msg)
        (flush)
        (System/exit 1)))))
