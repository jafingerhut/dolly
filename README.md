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

## License

Copyright © 2014 Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
