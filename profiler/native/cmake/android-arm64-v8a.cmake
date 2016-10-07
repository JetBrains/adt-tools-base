set(CMAKE_SYSTEM_PROCESSOR "aarch64")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-arm64")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} --sysroot=${SYSROOT} -I${STL}/include -I${STL}/libs/arm64-v8a/include -L${STL}/libs/arm64-v8a")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} --sysroot=${SYSROOT}")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
