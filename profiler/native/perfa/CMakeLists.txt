add_library(perfa SHARED
            support/profiler_service.cc
            perfa.cc)

target_link_libraries(perfa
                      ProfilerProtos)
