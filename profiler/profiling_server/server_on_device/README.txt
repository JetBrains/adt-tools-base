This is a native server running on Android devcie, providing data for Android Studio Profilers.

### How to build?

1. Intall Android NDK.
2. Type 'ndk-build' in this directory ("tools/base/profiler/profiling_server/server_on_device").

### How to deploy?

adb push libs/armeabi/studio_profiling_server /data/local/tmp

### How to run?

Run the binary directly from adb shell.
