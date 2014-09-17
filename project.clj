(defproject jafingerhut/dolly "0.1.0-SNAPSHOT"
  :description "Clojure namespace cloning"
  :url "http://github.com/jafingerhut/dolly"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 ;; Right now 0.2.7-SNAPSHOT is Andy Fingerhut's
                 ;; locally modified version of tools.namespace 0.2.6
                 ;; plus his proposed tns-20-v2.patch fix for issue
                 ;; TNS-20.
                 ;;[org.clojure/tools.namespace "0.2.7-SNAPSHOT"]
                 [rhizome "0.2.1"]]
  :eval-in-leiningen true)
