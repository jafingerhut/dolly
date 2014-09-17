#! /bin/bash

# Get an unmodified copy of tools.namespace repo, then patch in my
# local modifications.

set -x

git clone git://github.com/clojure/tools.namespace.git
cd tools.namespace

# This is the commit shortly after tag tools.namespace-0.2.6 was
# created that sets up pom.xml to make the version 0.2.7-SNAPSHOT
# instead of 0.2.6
git checkout 25f5806c318ec413451341841b459777ae70ec69

# This is my proposed patch for ticket TNS-20 that seems to correct
# the unload order in trackers.
patch -p1 < ../tns-20-v2.patch
