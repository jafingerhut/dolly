#! /bin/bash

# Get an unmodified copy of tools.namespace repo, then patch in my
# local modifications.

set -x

git clone git://github.com/clojure/tools.namespace.git
cd tools.namespace

git checkout tools.namespace-0.2.7
