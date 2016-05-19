cmake_minimum_required(VERSION 2.8)

include(../cmake/proto_compile.cmake)

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

set(PROTO_FILES profiler_service.proto)

PROTOBUF_GENERATE_CPP(CPP_PROTO_SRCS CPP_PROTO_HDRS ../proto ${PROTO_FILES})
PROTOBUF_GENERATE_GRPC(GRPC_PROTO_SRCS GRPC_PROTO_HDRS ../proto ${PROTO_FILES})

set(PROTO_DEPENDENCIES ${CPP_PROTO_SRCS}
                       ${CPP_PROTO_HDRS}
                       ${GRPC_PROTO_SRCS}
                       ${GRPC_PROTO_HDRS})

set(LINK_DEPENDENCIES dl
                      grpc++_unsecure
                      grpc_unsecure
                      gpr
                      libprotobuf
                      zlibstatic)

if(ANDROID)
  set(LINK_DEPENDENCIES ${LINK_DEPENDENCIES}
                        log
                        gnustl_static)
endif()

include_directories(${CMAKE_CURRENT_BINARY_DIR}
                    ${PROTOBUF_INCLUDE_DIR}
                    ${GRPC_INCLUDE_DIR})

add_executable(perfd
               perfd.cc
               profiler_service.cc
               ${PROTO_DEPENDENCIES})

target_link_libraries(perfd
                      ${LINK_DEPENDENCIES})
