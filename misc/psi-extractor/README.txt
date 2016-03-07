PSI Subset Library Extraction Tool

The PSI subset library is a strict subset of IntelliJ IDEA's PSI API,
as well as the transitive dependencies of those APIs. This constitutes
around 470 classes (not including inner classes), and weighs in around
700K.

The purpose of this library is to expose the Java AST and type
resolution aspects of PSI, such that they can be used by Android
Lint's various checks.

There are three primary reasons for this library:

(1) Lint had to change APIs anyway, since the Lombok AST API it was
using before did not support Java 7 (which lint worked around with
some hacks), and also does not support Java 8, which lint can't work
around.

(2) Lombok AST does not support type resolution, or reference
resolution (such as looking up which method definition a given call
references, and so on. Lint had worked around this by building up a
separate hierarchy, the "ResolvedNodes". Initially, lint checks could
only perform these lookups when running inside the IDE (since this was
a bridge to the underlying PSI machinery), but lint was later enhanced
to also support this when running outside of the IDE (built on top of
ECJ), and fact that these two hierarchies are separate cause several
problems. The most notable one is that after resolving a symbol to a
method, you can't then go looking inside that method's AST nodes.

(3) When lint runs inside the IDE (where it runs all the time in the
background of the editor, where performance is paramount), it has to
convert the full AST of the current source file to the Lombok AST,
each and every time. By switching the lint checks to be based on PSI,
lint can be more efficient when running in the IDE: it can reuse the
IDE's existing PSI datastructures directly.

Note that this library only includes

(1) the read-only aspects of PSI; many PSI nodes include setters too
(for changing code elements). Those are stripped out when we extract
the library.

(2) the PSI APIs, not the PSI implementations (the indices, the
services, the parsers, etc). When lint runs on the command line
(typically from Gradle), it has its own implementation of the PSI APIs
sitting on top of ECJ data structures.


Note that many PSI classes include parts outside of PSI. For example,
the core PSI class, PsiElement, has methods like getProject() (which
points to its project model) and getIcon() (for returning icons
relevant to this element). And many classes include method bodies
which point outside of PSI. In order to be able to extract this code,
the extraction both removes methods from the APIs that it extracts (so
PsiElement for example does not include getProject() in our subset
library). And in some cases it wants to include a method, but it can't
include the method *body*. An example of this is PsiDisjunctionType,
where the constructor calls into a bunch of code we can't use. In that
case, the extraction code will replace the method body with a custom
version.

Extraction works as follows:

It builds up a call graph, showing which classes, fields and methods
reference which other classes, fields and methods. We then mark all
the ones in the PSI packages as included. And then we apply a bunch of
exceptions to that rule (for classes in the PSI package that don't fit
our PSI subset criteria). Similarly, we remove methods and fields that
have been blacklisted. We then scan through the graph and flow from
included node to included node, computing what's reachable (and making
sure that a reachable node never reaches a blacklisted node). At the
end of that analysis, we write the resulting classes out to a jar file
(and package it as a local Maven artifact).

(Note: The extraction is performed on the binary library (the Studio
installation). The source directory is only used for output: we write
the artifact into prebuilts, and the signature file into the
extraction tool source directory.)

USAGE:

Run "gradle" to build the extraction binary. This will be installed
under out/build/, so you can invoke it like this:

../../../../out/build/base/psi-extractor/build/install/psi-extractor/bin/psi-extractor
(add .bat on Windows)

It takes two mandatory parameters:

(1) the full path to the Android Studio (or IntelliJ IDEA) IDE you
    want to based the PSI libraries on. Typically after merging a new
    version of IntelliJ (which occasionally changes PSI APIs) into
    Studio, the extraction should be repeated to ensure that lint
    rules running within the IDE are following the exact same APIs.

(2) the full path to the Android Studio source tree (e.g. the folder
    which contains tools/base, tools/adt/idea, prebuilts/tools/, etc.
    This is where the outputs are written:

    (a) the artifact itself is written into prebuilts/tools/m2/repository,
        e.g. m2/repository/com/android/tools/external/com-intellij/...

    (b) the "signatures" (class names and the methods and fields
        included) are written to a signatures.txt file in the
        extraction library project. This can be used to diff and judge
        changes in APIs after merges or changes to the extraction
        algorithm to ensure that we're including precisely the API
        surface from PSI that we intend to.

Example:
$ cd $TOP where TOP = tools/base/misc/psi-extractor
$ gradle && \
cd ../../../.. && \
out/build/base/psi-extractor/build/install/psi-extractor/bin/psi-extractor \
/Applications/Android\ Studio.app/ \
`pwd`

(This runs the default Gradle target in the project, which is the
installApp task.)
