cmake_minimum_required(VERSION 2.8)

include(cmake/proto_compile.cmake)

if (NOT DEFINED ENV{GRPC_ROOT_DIR})
  message( FATAL_ERROR "Environment variable GRPC_ROOT_DIR not set." )
  return()
endif()

if(NOT CMAKE_CROSSCOMPILING)
  if(NOT HOST_BUILD_DIR)
    set(HOST_BUILD_DIR ${CMAKE_CURRENT_BINARY_DIR})
  endif()
endif()

if(HOST_BUILD_DIR)
  if(NOT PROTOBUF_PROTOC_EXECUTABLE)
    set(PROTOBUF_PROTOC_EXECUTABLE ${HOST_BUILD_DIR}/protoc)
  endif()

  if(NOT GRPC_CPP_PLUGIN_PATH)
    set(GRPC_CPP_PLUGIN_PATH ${HOST_BUILD_DIR}/grpc_cpp_plugin)
  endif()
endif()

if(NOT GRPC_INCLUDE_DIR)
  set(GRPC_INCLUDE_DIR $ENV{GRPC_ROOT_DIR}/include)
endif()

if(NOT PROTOBUF_INCLUDE_DIR)
  set(PROTOBUF_INCLUDE_DIR $ENV{GRPC_ROOT_DIR}/third_party/protobuf/src)
endif()

add_subdirectory($ENV{GRPC_ROOT_DIR} ./grpc EXCLUDE_FROM_ALL)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

add_subdirectory(perfd)
