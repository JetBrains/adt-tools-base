cmake_minimum_required(VERSION 2.8)

# Configure HOST_BUILD_DIR
if((NOT CMAKE_CROSSCOMPILING) AND (NOT HOST_BUILD_DIR))
  if (CMAKE_RUNTIME_OUTPUT_DIRECTORY)
    set(HOST_BUILD_DIR ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
  else()
    set(HOST_BUILD_DIR ${CMAKE_CURRENT_BINARY_DIR})
  endif()
endif()

if(HOST_BUILD_DIR)
  set(PROTOBUF_PROTOC_EXECUTABLE ${HOST_BUILD_DIR}/protoc)
  set(GRPC_CPP_PLUGIN_PATH ${HOST_BUILD_DIR}/grpc_cpp_plugin)
endif()

# Set ROOT dir for every third party dependency
set(GRPC_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/grpc)
set(GTEST_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/gtest)
set(GMOCK_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/gmock)

# Include additional cmake modules after setting global variables
include(cmake/proto_compile.cmake)
include(cmake/test.cmake)

# Include gRPC
set(GRPC_INCLUDE_DIR ${GRPC_ROOT_DIR}/include)
set(PROTOBUF_INCLUDE_DIR ${GRPC_ROOT_DIR}/third_party/protobuf/src)
add_subdirectory(${GRPC_ROOT_DIR} ./grpc EXCLUDE_FROM_ALL)

# Set command line flags for profiler code
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

# Add profiler components
add_subdirectory(perfd)
add_subdirectory(utils)
