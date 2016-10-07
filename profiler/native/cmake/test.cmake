# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

include(${CMAKE_CURRENT_LIST_DIR}/adb.cmake)

# Create targets for gtest
if(NOT GTEST_ROOT_DIR)
  message(FATAL_ERROR "GTEST_ROOT_DIR not set.")
  return()
endif()

add_library(gtest ${GTEST_ROOT_DIR}/src/gtest-all.cc
                  ${GTEST_ROOT_DIR}/src/gtest_main.cc)

target_include_directories(gtest PUBLIC ${GTEST_ROOT_DIR}
                                        ${GTEST_ROOT_DIR}/include)

# Create targets for gmock
if(NOT GMOCK_ROOT_DIR)
  message(FATAL_ERROR "GMOCK_ROOT_DIR not set.")
  return()
endif()

add_library(gmock ${GMOCK_ROOT_DIR}/src/gmock-all.cc)

target_include_directories(gmock PUBLIC ${GMOCK_ROOT_DIR}
                                        ${GMOCK_ROOT_DIR}/include
                                        ${GTEST_ROOT_DIR}/include)

add_dependencies(gmock gtest)

# Collect the list of libraries required to be linked into every test
# executable
set(GTEST_LINK_LIBRARIES gtest
                         gmock)

if(ANDROID)
  set(GTEST_LINK_LIBRARIES ${GTEST_LINK_LIBRARIES} gnustl_static)
else()
  set(GTEST_LINK_LIBRARIES ${GTEST_LINK_LIBRARIES} pthread)
endif()

# Copy test data files to generated directory.
if(ANDROID)
  run_adb_command(copy-testdata
                  push ${CMAKE_CURRENT_SOURCE_DIR}/testdata/* /data/local/tmp)
else()
  add_custom_target(copy-testdata
                    COMMAND ${CMAKE_COMMAND} -E copy_directory
                            ${CMAKE_CURRENT_SOURCE_DIR}/testdata
                            ${CMAKE_CURRENT_BINARY_DIR})
endif()

if(ANDROID)
  function(run_unit_test name)
    file(RELATIVE_PATH file_path ${CMAKE_BINARY_DIR} ${CMAKE_CURRENT_BINARY_DIR})

    # Copy the test file to the android device
    run_adb_command(push-${name}
                    push ${CMAKE_CURRENT_BINARY_DIR}/${name} /data/local/tmp/${file_path}/${name})
    add_dependencies(push-${name} ${name})

    # Run the test on the android device
    run_adb_command(check-${name}
                    shell 'cd /data/local/tmp/${file_path} && /data/local/tmp/${file_path}/${name}')
    add_dependencies(check-${name} push-${name})
  endfunction()
else()
  function(run_unit_test name)
    add_custom_target(check-${name}
                      COMMAND ${name})
    add_dependencies(check-${name} ${name})
  endfunction()
endif()

# Cretae target to run all unit test
add_custom_target(check)

# Create function for adding unit tests
function(add_unit_test name)
  # Compile the executable for the test
  add_executable(${name} ${ARGN})
  target_include_directories(${name} PUBLIC ${GTEST_ROOT_DIR}/include
                                            ${GMOCK_ROOT_DIR}/include)
  set_target_properties(${name} PROPERTIES
                        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})

  # Create custome target for running the test and set it as a dependency of
  # check so it is included in it
  run_unit_test(${name})
  add_dependencies(check-${name} copy-testdata)
  add_dependencies(check check-${name})
endfunction()

