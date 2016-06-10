add_library(profiler_memory
            memory_data_cache.cc
            memory_collector.cc
            memory_service.cc)

target_link_libraries(profiler_memory
                      ProfilerProtos)
