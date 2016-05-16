add_library(ProfilerNetwork
            connection_data_collector.cc
            network_data_collector.cc
            profiler_server_network.cc
            traffic_data_collector.cc)

target_link_libraries(ProfilerNetwork
                      ProfilerProtos
                      ProfilerUtils)

add_unit_test(profiler-network-test
              connection_data_collector_test.cc
              network_data_collector_test.cc
              traffic_data_collector_test.cc)

target_link_libraries(profiler-network-test
                      ProfilerNetwork
                      ${GTEST_LINK_LIBRARIES})
