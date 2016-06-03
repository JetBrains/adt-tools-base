add_library(profiler_cpu
            cpu_cache.cc
            cpu_profiler_service.cc
            cpu_sampler.cc
            cpu_usage_data_collector.cc)

target_link_libraries(profiler_cpu
                      ProfilerProtos
                      ProfilerUtils)
