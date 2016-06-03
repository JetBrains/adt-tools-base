add_executable(perfd
               perfa_service.cc
               perfd.cc
               profiler_service.cc)

target_link_libraries(perfd
                      profiler_cpu
                      ProfilerProtos)
