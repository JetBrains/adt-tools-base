set(UTIL_SOURCES
    activity_manager.cc
    bash_command.cc
    installer.cc
    package_manager.cc
    profiler_file.cc
    file_reader.cc
    stopwatch.cpp
    timestamp.cc)

if (CMAKE_HOST_APPLE AND NOT ANDROID)
    set(UTIL_SOURCES ${UTIL_SOURCES} clock.mac.cpp)
else()
    set(UTIL_SOURCES ${UTIL_SOURCES} clock.cpp)
endif()

add_library(ProfilerUtils
            ${UTIL_SOURCES})

add_unit_test(profiler-utils-test
              file_reader_test.cc
              time_value_buffer_test.cc
              timespec_math_test.cc
              stopwatch_test.cc)

target_link_libraries(profiler-utils-test
                      ProfilerUtils
                      ${GTEST_LINK_LIBRARIES})
