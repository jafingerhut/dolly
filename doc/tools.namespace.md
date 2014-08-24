The keys of the tracker returned by dir/scan-all are as follows:

key: :clojure.tools.namespace.dir/time
Probably a time in seconds since Jan 1 1970
Example value: 1408341929197

key: :clojure.tools.namespace.dir/files
A set of java.io.File objects for files that were scanned,
whether they contain ns forms in them or not.

key: :clojure.tools.namespace.file/filemap
A map from java.io.File objects to symbols, where the files are
those that were scanned, and the symbols are the names of the
namespaces defined in those files.  A file that was read will
not be in this map if no ns form was found while reading it.
Example value (partial):
{#<File /Users/jafinger/clj/andy-forks/eastwood/cases/testcases/tanal_31.clj>
 testcases.tanal-31,
 #<File /Users/jafinger/clj/andy-forks/eastwood/cases/testcases/f05.clj>
 testcases.f05}

key: :clojure.tools.namespace.track/deps
A map containing the following keys:
    :dependencies
    A map from namespaces (given as symbols), to sets of
    namespaces (also given as symbols).  If the key is
    namespace A, then the value is the set of namespaces that A
    mentions in :require or :use statements in its ns form.

    :dependents
    Similar to :dependencies above, and again the keys are
    namespaces (given as symbols), and the values are sets of
    namespaces (also given as symbols).  If the key is
    namespace A, then the value is the set of namespaces that
    :require or :use A in their ns forms.

key: :clojure.tools.namespace.track/load

A list of symbols that are the namespaces in those files,
sorted in an order that contains all things :require'd or
:use'd in ns forms, before the namespace that :require'd or
:use'd them.  That is, it is one of potentially many
topologically sorted orders of the DAG where the nodes are
namespaces, and there is a directed edge from namespace A to
namespace B if B :require's or :use's A.  Thus if namespaces
were loaded in this order, there should be nothing undefined
when a namespace is loaded.

key: :clojure.tools.namespace.track/unload
Similar to the /load key above.  The order is not guaranteed to
be exactly the reverse of the order of /load, but it should be
in an order that contains every namespace in an ns form of
those files, before any namespace it :require's or :use's.
