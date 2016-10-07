set(CMAKE_SYSTEM_PROCESSOR "armv7-a")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-arm")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mthumb --sysroot=${SYSROOT} -I${STL}/include -I${STL}/libs/armeabi-v7a/include -L${STL}/libs/armeabi-v7a")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} --sysroot=${SYSROOT}")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
