set(PROTO_FILES network_profiler.proto
                memory_profiler_service.proto
                perfa_service.proto
                profiler.proto
                profiler_service.proto)

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

add_library(ProfilerProtos
            ${PROTO_DEPENDENCIES})

target_link_libraries(ProfilerProtos
                      ${LINK_DEPENDENCIES})
