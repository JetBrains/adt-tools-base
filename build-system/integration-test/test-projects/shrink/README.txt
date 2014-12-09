This is a test project which exercises the "resource shrinking"
(removing unused resources) facility.

The project has three build types; only the "release" type has
shrinking enabled. To test, run "gradle assembleRelease", which will
generate statistics. ShrinkTest#"check shrink resources" will not only
build that target, but run specific checks on the results after the
build to make sure all referenced resources are still present, and
that all unused resources have been removed from the final APK.

The resource shrinking feature currently only removes file-based
resources (such as layouts, menus, and drawables), not value-based
resources (such as strings and dimensions). The latter requires aapt
support. Therefore, this test project currently only contains resource
tests relevant to file-based resources.

The project consists of a number of resources. Resources that are used
are named "used<number>"; resources that are unused are named
"unused<number>".

In general there should be a comment in each resource explaining why
the resource is or isn't referenced.

Second, there should be a unique resource for each individual
condition or scenario that we want to test, to ensure that we are
really truly checking each usecase.
