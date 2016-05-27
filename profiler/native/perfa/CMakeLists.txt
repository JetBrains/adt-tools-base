add_library(perfa SHARED
            perfa.cc)

target_link_libraries(perfa
                      ProfilerProtos)
