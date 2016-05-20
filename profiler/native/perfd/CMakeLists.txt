set(PROTO_FILES profiler_service.proto)

PROTOBUF_GENERATE_CPP(CPP_PROTO_SRCS CPP_PROTO_HDRS ../../proto ${PROTO_FILES})
PROTOBUF_GENERATE_GRPC(GRPC_PROTO_SRCS GRPC_PROTO_HDRS ../../proto ${PROTO_FILES})

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
