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

if(NOT ANDROID)
  set(GTEST_LINK_LIBRARIES ${GTEST_LINK_LIBRARIES} pthread)
endif()

# Cretae target to run all unit test
add_custom_target(check)

# Create function for adding unit tests
function(add_unit_test name)
  # Compile the executable for the test
  add_executable(${name} ${ARGN})
  target_include_directories(${name} PUBLIC ${GTEST_ROOT_DIR}/include
                                            ${GMOCK_ROOT_DIR}/include)
  target_link_libraries(${name} ${GTEST_LINK_LIBRARIES})

  if (ANDROID)
    target_link_libraries(${name} gnustl_static)
  endif()

  # Create custome target for running the test and set it as a dependency of
  # check so it is included in it
  add_custom_target(check-${name}
                    COMMAND ${name})
  add_dependencies(check check-${name})
endfunction()

