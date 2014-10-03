Steps to prepare in making a new release:

Change any snapshot versions of dependencies in project.clj to
non-snapshot versions.

Redo any unit tests 'lein test', and check for any changes in
behavior.  Compare against output of previous release.  Update
changes.md and changes-detailed.md with any differences in behavior.


Places where version number should be updated:

* project.clj just after jafingerhut/dolly
* README.md in install instructions, and instructions for developers
* src/leiningen/dolly.clj var dolly-version-string

Update the change log in changes.md

Commit all of those changes.

Tag it with a version tag, e.g.:

    % git tag -a dolly-0.1.0

I don't put much into the commit comments other than 'Dolly version
0.1.0'

'git push' by default does not push tags to the remote server.  To
cause that to happen, use:

    % git push origin --tags

Then deploy a release to Clojars.org:

* TBD: Add instructions for that here, including the potentially
  longer 'what was needed to do it the first time', perhaps later
  below.


When that is complete, then pick the next version number, at least a
temporary one for development purposes, and update it in these places
with -SNAPSHOT appended:

* project.clj just after jafingerhut/dolly
* README.md instructions for developers (if not already done above)
* src/leiningen/dolly.clj var dolly-version-string
