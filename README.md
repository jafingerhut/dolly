# Dolly

Primary goal: Make it easy to clone source code of other Clojure
projects into your Clojure project, with the option to rename the
namespaces.

Secondary goals: Provide utilities to Clojure developers for
investigating dependencies between namespaces within your Leiningen
project, and between Java classes and interfaces.

TBD: More documentation has been written for features than code has
been written to implement them.  For now, don't believe this README,
believe the code.


## Motivation

The primary goal of cloning source code is probably most useful for
developing [Leiningen][Leiningen] [plugins][plugin], but there may be
other use cases I have not thought of.  The motivation for creating
Dolly was to assist in developing the [Eastwood][Eastwood] Clojure
lint tool.

[Leiningen]: http://leiningen.org/
[plugin]: https://github.com/technomancy/leiningen/blob/stable/doc/PLUGINS.md
[Eastwood]: https://github.com/jonase/eastwood

Formerly Eastwood had dependencies on commonly used Clojure libraries
like [`core.memoize`][cmemoize] and [`core.cache`][ccache].  During
linting, Eastwood reads, analyzes, and _evaluates_ Clojure source code
of projects being linted.

[cmemoize]: https://github.com/clojure/core.memoize
[ccache]: https://github.com/clojure/core.cache

If a linted project also used `core.memoize` or `core.cache`, then
because they have the same namespaces, only one version of those
namespaces can be loaded into Clojure.  If the linted project and
Eastwood use identical versions of the library, there are no problems.

However, if the linted project and Eastwood used different versions of
the libraries, and those versions had differences in their API, then
either Eastwood or the linted project would get exceptions due to
missing functions, function calls with incorrect number of arguments,
etc.

To avoid this problem, Eastwood now has in its own source code a copy
of `core.cache`, `core.memoize`, and several other Clojure libraries.
The namespaces of these libraries have been renamed, e.g.
`clojure.core.cache` has been renamed
`eastwood.copieddeps.dep4.clojure.core.cache`.  Eastwood only uses the
renamed version for its own purposes, leaving the namespace
`clojure.core.cache` available for the linted project's use.

There is currently no need for communication of data between
Eastwood's renamed version and the projects being linted.  If there
were such a need, this copy-and-rename technique would not be
sufficient in all cases.

For the first few versions of Eastwood that used this technique, this
copying and renaming was done manually.  While not difficult, this is
tedious, and makes it more time consuming to upgrade to newer versions
of the libraries that Eastwood uses as they become available.  Thus
Dolly was born.

Stuart Sierra created the [`tools.namespace`][tnamespace] library,
including its `clojure.tools.namespace.move` namespace, which
implements most of what Dolly does.  It renames a namespace that is
already part of a Clojure project.  What Dolly adds to this is
primarily a way to iterate this over many namespaces, and to first
copy in the source code to a temporary 'staging' directory in your
project before it is then renamed and moved to a more permanent
directory.

[tnamespace]: https://github.com/clojure/tools.namespace

A potential side benefit of this copy-and-rename technique is that you
may easily modify your copy of the library, e.g. if there are bugs in
it or enhancements appropriate for your use case.  That leaves the
responsibility on you to maintain such modifications if and when you
move to newer versions of that library.


## Installation and Quick Usage

Put `[jafingerhut/dolly "0.1.0"]` into the `:plugins` vector
of your `:user` profile, or if you are on Leiningen 1.x do `lein
plugin install dolly 0.1.0`.

FIXME: and add an example usage that actually makes sense:

    $ lein dolly


## Usage

Things you can do with Dolly:

* Show all namespaces with their dependencies
* TBD: List, add, upgrade, and remove namespaces cloned from other projects
* TBD: Show Java classes and interfaces with their extends/implements relationships


### List, add, upgrade, remove namespaces cloned from other projects

Note: As always, it is your responsibility to respect the licenses of
code you use in your projects.  Dolly makes no attempt to check for
license compatibility.

WARNING: Assume Dolly has bugs, and take appropriate steps to protect
your source code.  It would be foolhardy to use these commands on the
only copy of your source code, or to commit the changed version to
anything but an experimental development branch, until the changes
have been reviewed and tested.

Information about namespaces that Dolly has been used to clone into
your project are stored in a file `dolly.edn` in the root directory of
your project.  This file should be kept under revision control with
the rest of the files in your project.

List info about any namespaces that Dolly has previously been used to
clone into your project:

    $ lein dolly list-clones
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
    $ lein dolly ns '{ <option key/value pairs> }'

By default the namespace dependencies are shown in text format.  For
example, here is part of the text output for `tools.nrepl` version
0.2.4:

    ; Number here indicates first time namespace appears in output.
    ; If no leading number, it also appeared earlier.
    ; |
    ; |     Number in brackets afterwards indicates where namespace
    ; |     was first shown, and that it had children shown.
    ; |     If no leading or trailing number, no children were shown.
    ; |                                       |
    ; V                                       V

    Dependencies:
      1 clojure.tools.nrepl.helpers
      2   clojure.tools.nrepl.middleware.load-file
      3     clojure.tools.nrepl.middleware
      4       clojure.tools.nrepl
      5         clojure.tools.nrepl.misc
      6         clojure.tools.nrepl.transport
      7           clojure.tools.nrepl.bencode
                  clojure.tools.nrepl.misc
              clojure.tools.nrepl.misc
              clojure.tools.nrepl.transport  [6]
      8     clojure.tools.nrepl.middleware.interruptible-eval
              clojure.tools.nrepl.middleware  [3]
      9       clojure.tools.nrepl.middleware.pr-values
                clojure.tools.nrepl.middleware  [3]
                clojure.tools.nrepl.transport  [6]
              clojure.tools.nrepl.misc
              clojure.tools.nrepl.transport  [6]

     [ rest of output omitted for brevity ]

You may also use the long form 'namespaces' instead of 'ns'.  The
default behavior is:

* Show all namespaces defined in some Clojure file in your Leiningen
  project `:source-paths` and their subdirectories.
* Also show any namespaces required or used by those namespaces.
* Do not show namespaces that are part of Clojure,
   e.g. `clojure.core`, `clojure.set`, `clojure.string`, etc. since
   these are so common.

`:source-paths` is the vector of one directory `[ "src" ]` if you do
not override it in your Leiningen `project.clj` file.

Options controlling the form of output:

* Show namespace dependencies in text on the standard output (default)
* Create a Graphviz dot file
* Create a PNG file
* Create a window showing the dependency graph

Options controlling the namespaces to show:

* By default the directories to search for Clojure source files is
  given by the value of the `:source-paths` key in your Leiningen
  project.  You can specify your own vector of directories to search
  after the `:paths` key in the options map.  Give directory names in
  double-quoted strings.  If the keywords `:source-paths` or
  `:test-paths` appear in the vector, they will be expanded to the
  values of those keys in your Leiningen project, but flattened so the
  result is a vector of strings.

* TBD: Specify a set of namespaces to include or exclude
  * Default is to exclude showing namespaces that are part of Clojure,
    e.g. `clojure.core`, `clojure.set`, `clojure.string`, etc. since
    these are so common.
  * TBD exactly how.  Maybe an input text file in edn format?
  * TBD: Allow wildcards like `tools.analyzer.jvm.*` ?

TBD: Options for controlling how to abbreviate namespaces shown.
Making long.name.spaces.shorter can go a long way to making the output
easier to read.

TBD: Look at these projects for inspiration and ideas here.  Don't be
afraid to create more options for controlling the output.

* [`lein-ns-dep-graph`](https://github.com/hilverd/lein-ns-dep-graph)
* [`nephila`](https://github.com/timmc/nephila)
* [`lacij`](https://github.com/pallix/lacij)


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
