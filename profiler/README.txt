1. Go to profiler/ directory in terminal. First Proto files are compiled
   manually before library is set up. Then the following command should compile
   all APP_MODULES in Application.mk.

   ndk-build NDK_PROJECT_PATH=$(pwd) APP_BUILD_SCRIPT=$(pwd)/Android.mk \
   NDK_APPLICATION_MK=$(pwd)/Application.mk

2. Use "adb push" to push binary to the testing device.

   adb push libs/x86/unittests /data/local/tmp/unittests

3. Run on device to see unit tests' result.

   adb shell /data/local/tmp/unittests

