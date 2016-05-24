add_library(ProfilerNetwork
            connection_data_collector.cc
            network_data_collector.cc
            profiler_server_network.cc
            traffic_data_collector.cc)

target_link_libraries(ProfilerNetwork
                      ProfilerProtos)
