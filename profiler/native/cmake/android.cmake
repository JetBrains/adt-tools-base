set( CMAKE_SYSTEM_NAME Linux )
set( ANDROID True )

# Set the linker flags
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS} -pie" )

# Enable link time garbage collection to reduce the binary size
set( CMAKE_CXX_FLAGS           "${CMAKE_CXX_FLAGS} -fdata-sections -ffunction-sections" )
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS} -Wl,--gc-sections" )
set( CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections" )

# Store the compiler and the linker flags in the cache
set( CMAKE_C_FLAGS             "${CMAKE_C_FLAGS}"             CACHE STRING "C flags" )
set( CMAKE_CXX_FLAGS           "${CMAKE_CXX_FLAGS}"           CACHE STRING "CXX flags" )
set( CMAKE_EXE_LINKER_FLAGS    "${CMAKE_EXE_LINKER_FLAGS}"    CACHE STRING "Exevutable Library linker flags" )
set( CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}" CACHE STRING "Shared Library linker flags" )
