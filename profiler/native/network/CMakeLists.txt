add_library(ProfilerNetwork
            connection_sampler.cc
            network_collector.cc
            network_sampler.cc
            traffic_sampler.cc)

target_link_libraries(ProfilerNetwork
                      ProfilerProtos
                      ProfilerUtils)

add_unit_test(profiler-network-test
              connection_sampler_test.cc
              network_sampler_test.cc
              traffic_sampler_test.cc)

target_link_libraries(profiler-network-test
                      ProfilerNetwork
                      ${GTEST_LINK_LIBRARIES})
