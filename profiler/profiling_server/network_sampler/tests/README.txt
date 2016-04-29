1. Go to network_sampler/tests directory in terminal, compile native code:

   ndk-build NDK_PROJECT_PATH=$(pwd) APP_BUILD_SCRIPT=$(pwd)/Android.mk \
   NDK_APPLICATION_MK=$(pwd)/Application.mk

2. Use "adb push" to push binary to the testing device.

   adb push libs/x86/network_sampler_unittest /data/local/tmp/network_sampler_unittest

3. Run on device to see unit tests' result.

   adb shell /data/local/tmp/network_sampler_unittest
