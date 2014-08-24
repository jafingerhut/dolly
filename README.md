# dolly

Goal: Become a Leiningen plugin to clone source code of other Clojure
projects into your Clojure project, optionally with the namespaces
renamed.

This is useful in a limited set of circumstances.  For example, the
Eastwood Clojure lint tool copies the source code of several other
Clojure libraries into it, and renames them, because then it can use a
particular version of those libraries, and yet still analyze and eval
the code of projects it lints that also use those libraries, without
conflict.

Secondary goals: Implement developer utilities for investigating
dependencies between namespaces within your Leiningen project, and
between Java classes and interfaces.


## Installation and Quick Usage

Put `[jafingerhut/dolly "0.1.0-SNAPSHOT"]` into the `:plugins` vector
of your `:user` profile, or if you are on Leiningen 1.x do `lein
plugin install dolly 0.1.0-SNAPSHOT`.

FIXME: and add an example usage that actually makes sense:

    $ lein dolly


## Usage

Things you can do with dolly:

* TBD: List, add, upgrade, and remove namespaces cloned from other projects
* Show all namespaces with their dependencies
* TBD: Show Java classes and interfaces with their extends/implements relationships


### List, add, upgrade, remove namespaces cloned from other projects

Note: As always, it is your responsibility to repect the licenses of
code you use in your projects.  Dolly makes no attempt to check for
license compatibility.

WARNING: Assume this code has bugs, and take appropriate steps to
protect your source code.  It would be foolhardy to use these commands
on the only copy of your source code, or to commit the changed version
to anything but an experimental development branch, until the changes
have been reviewed and tested.

Information about namespaces that dolly has been used to clone into
your project are stored in a file `dolly.edn` in the root directory of
your project.  This file should be kept under revision control with
the rest of the files in your project.

List info about any namespaces that dolly has previously been used to
clone into your project:

    $ lein dolly ls

Add a new clone of a namespace hierarchy to your project:

    $ lein dolly add '{:source-dir "path/to/root/dir/of/code/to/copy"
                       :source-namespace root.ns.name.in.orig.source.code
                       :dest-dir "path/to/copy/target/dir/in/your/project"
                       :dest-namespace new.root.ns.name.in.your.project}'

Remove a previously-added clone from your project:

    $ lein dolly remove '{:namespace root.ns.name.in.your.project}'

Upgrade (or downgrade) a previously-added clone in your project:

    $ lein dolly upgrade [ TBD: same opts as add command above? ]

TBD: First implement ls, add, and remove.  upgrade can be done by the
user via remove followed by add of the new source code, so perhaps no
need to implement it at all.

`:source-dir` is the root directory containing all of the source
code you wish to copy (actually copy-and-modify-namespace-names).

`:source-namespace` specifies the root namespace in the original
source code that should be renamed to the value of `:dest-namespace`
while copying-and-modifying the original source code into your
project.

`:dest-namespace` specifies a root namespace in the current project
that will be added, upgraded, or removed.  For example, it might be
the same as the original namespace you are copying from, except with a
prefix added so that it fits naturally within the namespaces of your
own project.

Examples:

    $ lein dolly add '{:source-dir "path/to/tools.analyzer/src/main/clojure"
                       :source-namespace clojure.tools.analyzer
                       :dest-dir "src/eastwood/copieddeps/dep2/clojure/tools/analyzer"
                       :dest-namespace eastwood.copieddeps.dep2.clojure.tools.analyzer'}
    
    $ lein dolly remove '{:namespace eastwood.copieddeps.dep2.clojure.tools.analyzer}'

When doing `add`, all Clojure source files (i.e. those with file names
ending in `.clj`) in the directory `:source-dir` and its
subdirectories are found.  All are copied into your project.  They are
copied into files with the same directory structure beneath the
directory given by `:dest-dir`.

For all source files that were already in your project before doing
`lein dolly add`, as well as the new files being copied in, all
occurrences of the namespace `:source-namespace` are replaced with
`:dest-namespace`.  Thus your entire project will use the copied-in
version rather than the original one.  This replacement is done via
textual search-and-replace, without regard for whether the original
string is actually a namespace in your Clojure code, or it is within a
string, comment, etc.

In addition, if the newly added files use any namespaces that were
previously added via `lein dolly add`, those namespaces will be
replaced with the cloned versions.

TBD: Give example of this to make it clearer.


### Show all namespaces with their dependencies

    $ lein dolly ns

The default behavior is:

* Show all namespaces defined in some file in your Leiningen project
  `:source-paths` and their subdirectories.
* Also show any namespaces required or used by those namespaces.
* Do not show namespaces that are part of Clojure,
   e.g. `clojure.core`, `clojure.set`, `clojure.string`, etc. since
   these are so common.

`:source-paths` is the directory `src` if you do not override it in
your `project.clj` file.

Options controlling the form of output:

* Show them in text on the standard output
* Create a Graphviz dot file
* Create a PNG file
* Create a window showing the dependency graph

TBD: Look at these projects for inspiration and ideas here.  Don't be
afraid to create more options for controlling the output.

* [`lein-ns-dep-graph`](https://github.com/hilverd/lein-ns-dep-graph)
* [`nephila`](https://github.com/timmc/nephila)
* [`lacij`](https://github.com/pallix/lacij)


Options controlling the namespaces to show:

* TBD: Make it easy to explicitly specify a list of directories,
  optionally including the keywords `:source-paths` and/or
  `:test-paths`, which are replaced with their values from the
  Leiningen project.
* Specify a set of namespaces to include or exclude
  * Default is to exclude showing namespaces that are part of Clojure,
    e.g. `clojure.core`, `clojure.set`, `clojure.string`, etc. since
    these are so common.
  * TBD exactly how.  Maybe an input text file in edn format?
  * TBD: Allow wildcards like `tools.analyzer.jvm.*` ?


### Show Java classes and interfaces with their extends/implements relationships

Look here for ideas.

* [`class-diagram`](https://github.com/stuartsierra/class-diagram) -
  Very nice, but I would like additional options to control the set of
  classes/interfaces that are included, including to do
  subclasses/interfaces of a specified starting class/interface.

It would be nice if there were a simple way to get a list of all
classes and interfaces currently defined in the JVM (I realize that
can change dynamically over time -- at least some kind of snapshot
list, even if it was not guaranteed to be consistent with any one
point in time).

File `doc/clojure-class-interface-diagram.clj` was modified starting
from some code written by Chris Houser
[here](http://n01se.net/paste/6HN).


## License

Copyright © 2014 Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
