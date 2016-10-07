add_library(perfa SHARED
            support/profiler_service.cc
            perfa.cc)

target_link_libraries(perfa
                      ProfilerProtos
                      ProfilerUtils)

if(CMAKE_RUNTIME_OUTPUT_DIRECTORY)
  set_target_properties(perfa PROPERTIES
                        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
endif()
