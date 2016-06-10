set(UTILS_SOURCES
    activity_manager.cc
    bash_command.cc
    file_reader.cc
    installer.cc
    package_manager.cc
    profiler_file.cc
    stopwatch.cc
    timestamp.cc)

if (CMAKE_HOST_APPLE AND NOT ANDROID)
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.mac.cc)
else()
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.cc)
endif()

add_library(ProfilerUtils
            ${UTILS_SOURCES})

add_unit_test(profiler-utils-test
              file_reader_test.cc
              time_value_buffer_test.cc
              timespec_math_test.cc
              stopwatch_test.cc)

target_link_libraries(profiler-utils-test
                      ProfilerUtils
                      ${GTEST_LINK_LIBRARIES})
