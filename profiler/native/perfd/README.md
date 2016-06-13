To run perfd on an Android device...

1. Build `perfd` using steps in `.../tools/base/profiler/native/README.md`
1. If successful, binary lives in `.../out/studio/native/out/(arch)/`
1. `cd` to `.../out`
1. `adb push studio/native/out/(arch)/perfd /data/local/tmp/perfd/perfd`
1. `adb shell /data/local/tmp/perfd/perfd`

By pushing perfd from `.../out/`, you don't have to worry about the directory
being deleted out from under you when you rebuild.

Once running, press Ctrl-C anytime to kill perfd

