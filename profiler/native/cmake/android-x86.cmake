set(CMAKE_SYSTEM_PROCESSOR "x86")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-x86")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -m32" CACHE STRING "ASM Flags")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -m32 --sysroot=${SYSROOT} -I${STL}/include -I${STL}/libs/x86/include -L${STL}/libs/x86")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} -m32 --sysroot=${SYSROOT}")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
