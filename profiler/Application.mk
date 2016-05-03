APP_ABI := armeabi x86

APP_STL := c++_static

NDK_TOOLCHAIN_VERSION := clang

# Add both modules to let the build pick them up
APP_MODULES := utils profiler_server_network unittests
