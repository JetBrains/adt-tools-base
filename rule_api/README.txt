The Rule API is a library intended for view authors to add designtime
support for their custom views in Android layout editors.

NOTE: The API is *not* final and will very likely continue to change
incompatibly until we finish it and incorporate feedback.

The rule API attempts to be IDE agnostic, so it should not have
specific dependencies on any tools. IDE vendors building layout
editors should provide IDE-side implementations of the rule interfaces
such that they can interact with view rules.
