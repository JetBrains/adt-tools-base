add_library(profiler_cpu
            cpu_cache.cc
            cpu_profiler_service.cc
            cpu_collector.cc
            cpu_usage_sampler.cc)

target_link_libraries(profiler_cpu
                      ProfilerProtos
                      ProfilerUtils)
