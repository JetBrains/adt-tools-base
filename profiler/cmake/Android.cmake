# If we are doing a try compile then we won't have ABI set up so we have to do an early return
get_property( IS_IN_TRY_COMPILE GLOBAL PROPERTY IN_TRY_COMPILE )
if( IS_IN_TRY_COMPILE )
 return()
endif()


if ( CMAKE_HOST_APPLE )
  set ( HOST "darwin-x86" )
elseif ( CMAKE_HOST_UNIX )
  set ( HOST "linux-x86" )
else()
  message( FATAL_ERROR "Host platform not supported: ${CMAKE_HOST_SYSTEM}")
endif()

set( CMAKE_SYSTEM_NAME Linux )
set( ANDROID True )

# Arguments
# PREBUILTS: The directory where the android prebuilts are located
# HOST: The host architecture. Supported: darwin-x86, linux-x86
# ABI: The target architecture: http://developer.android.com/ndk/guides/abis.html

# Set up the directories and flags for the different ABIs
set( ABI "${ABI}" CACHE INTERNAL "Android ABI" FORCE )
if( ABI STREQUAL "x86" )
  message( FATAL_ERROR "TODO ${ABI}" )
elseif ( ABI STREQUAL "x86_64" )
  message( FATAL_ERROR "TODO ${ABI}" )
elseif ( ABI STREQUAL "armeabi-v7a" )
  set( TOOLCHAIN "arm-linux-androideabi" )
  set( TOOLCHAIN_ARCH "arm" )
  set( ARCH "arm" )
elseif ( ABI STREQUAL "arm64-v8a" )
  set( TOOLCHAIN "aarch64-linux-android" )
  set( TOOLCHAIN_ARCH "aarch64" )
  set( ARCH "arm64" )
else()
  message( FATAL_ERROR "Unsupported ABI value: ${ABI}" )
endif()


set( HOST "${HOST}" CACHE INTERNAL "Host Architecture" FORCE )
if( HOST STREQUAL "darwin-x86" )
elseif ( HOST STREQUAL "linux-x86" )
else()
  message( FATAL_ERROR "Unsupported HOST value: ${HOST}" )
endif()

set( CMAKE_C_COMPILER   "${PREBUILTS}/gcc/${HOST}/${TOOLCHAIN_ARCH}/${TOOLCHAIN}-4.9/bin/${TOOLCHAIN}-gcc" CACHE PATH "C compiler" )
set( CMAKE_CXX_COMPILER "${PREBUILTS}/gcc/${HOST}/${TOOLCHAIN_ARCH}/${TOOLCHAIN}-4.9/bin/${TOOLCHAIN}-g++" CACHE PATH "CXX compiler" )

# Set the linker flags
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS} -pie" )

# Enable link time garbage collection to reduce the binary size
set( CMAKE_CXX_FLAGS           "${CMAKE_CXX_FLAGS} -fdata-sections -ffunction-sections" )
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS} -Wl,--gc-sections" )
set( CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections" )

set ( STL "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9" )
set ( SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-${ARCH}" )

set( CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} --sysroot=${SYSROOT} -I${STL}/include -I${STL}/libs/${ABI}/include -L${STL}/libs/${ABI}" )
set( CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} --sysroot=${SYSROOT}" )

# Compile for armv7a using the thumb instruction set
if ( ABI STREQUAL "armeabi-v7a" )
  set( CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mthumb" )
endif()

# Use gold linker and enable safe ICF in case of x86, x86_64 and arm
if ( ABI STREQUAL "x86"    OR
     ABI STREQUAL "x86_64" OR
     ABI STREQUAL "armeabi-v7a")
  set( CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe" )
endif()

# Store the compiler and the linker flags in the cache
set( CMAKE_C_FLAGS             "${CMAKE_C_FLAGS}"             CACHE STRING "C flags" )
set( CMAKE_CXX_FLAGS           "${CMAKE_CXX_FLAGS}"           CACHE STRING "CXX flags" )
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS}"    CACHE STRING "Exevutable Library linker flags" )
set( CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}" CACHE STRING "Shared Library linker flags" )
