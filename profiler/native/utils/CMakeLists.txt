add_library(ProfilerUtils
            file_reader.cc
            clock.cpp
            stopwatch.cpp)

add_unit_test(profiler-utils-test
              file_reader_test.cc
              time_value_buffer_test.cc
              timespec_math_test.cc
              stopwatch_test.cc)

target_link_libraries(profiler-utils-test
                      ProfilerUtils
                      ${GTEST_LINK_LIBRARIES})
