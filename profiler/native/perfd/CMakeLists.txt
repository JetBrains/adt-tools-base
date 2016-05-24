add_executable(perfd
               perfd.cc
               profiler_service.cc)

target_link_libraries(perfd
                      ProfilerProtos)
