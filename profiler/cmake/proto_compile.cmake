# CMake - Cross Platform Makefile Generator
# Copyright 2000-2016 Kitware, Inc.
# Copyright 2000-2011 Insight Software Consortium
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
# 
# * Redistributions of source code must retain the above copyright
#   notice, this list of conditions and the following disclaimer.
# 
# * Redistributions in binary form must reproduce the above copyright
#   notice, this list of conditions and the following disclaimer in the
#   documentation and/or other materials provided with the distribution.
# 
# * Neither the names of Kitware, Inc., the Insight Software Consortium,
#   nor the names of their contributors may be used to endorse or promote
#   products derived from this software without specific prior written
#   permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

function(_protobuf_generate_cpp SRCS HDRS FILE_SUFFIX OUT_TYPE PLUGIN PROTOPATH)
  if(NOT ARGN)
    message(SEND_ERROR "Error: _protobuf_generate_cpp() called without any proto files")
    return()
  endif()

  set(_protobuf_include_path -I ${CMAKE_CURRENT_SOURCE_DIR})
  if(DEFINED PROTOBUF_IMPORT_DIRS)
    foreach(DIR ${PROTOBUF_IMPORT_DIRS})
      get_filename_component(ABS_PATH ${DIR} ABSOLUTE)
      list(FIND _protobuf_include_path ${ABS_PATH} _contains_already)
      if(${_contains_already} EQUAL -1)
          list(APPEND _protobuf_include_path -I ${ABS_PATH})
      endif()
    endforeach()
  endif()

  if(PLUGIN STREQUAL "")
    set(PLUGIN_COMMAND)
    set(PLUGIN_EXECUTABLE)
  else()
  	set(PLUGIN_COMMAND "--plugin" ${PLUGIN})
    string(REGEX REPLACE ".*=" "" PLUGIN_EXECUTABLE ${PLUGIN})
  endif()

  set(${SRCS})
  set(${HDRS})
  foreach(FIL ${ARGN})
    get_filename_component(ABS_DIR ${PROTOPATH} ABSOLUTE)
    get_filename_component(ABS_FIL "${PROTOPATH}/${FIL}" ABSOLUTE)
    get_filename_component(FIL_WE ${FIL} NAME_WE)

    list(APPEND ${SRCS} "${CMAKE_CURRENT_BINARY_DIR}/${FIL_WE}${FILE_SUFFIX}.cc")
    list(APPEND ${HDRS} "${CMAKE_CURRENT_BINARY_DIR}/${FIL_WE}${FILE_SUFFIX}.h")

    add_custom_command(
      OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/${FIL_WE}${FILE_SUFFIX}.cc"
             "${CMAKE_CURRENT_BINARY_DIR}/${FIL_WE}${FILE_SUFFIX}.h"
      COMMAND  ${PROTOBUF_PROTOC_EXECUTABLE}
      ARGS ${OUT_TYPE} ${CMAKE_CURRENT_BINARY_DIR} ${PLUGIN_COMMAND} ${_protobuf_include_path} "--proto_path=${ABS_DIR}" ${ABS_FIL}
      DEPENDS ${ABS_FIL} ${PROTOBUF_PROTOC_EXECUTABLE} ${PLUGIN_EXECUTABLE}
      COMMENT "Running C++ protocol buffer compiler on ${FIL}"
      VERBATIM )
  endforeach()

  set_source_files_properties(${${SRCS}} ${${HDRS}} PROPERTIES GENERATED TRUE)
  set(${SRCS} ${${SRCS}} PARENT_SCOPE)
  set(${HDRS} ${${HDRS}} PARENT_SCOPE)
endfunction()

function(PROTOBUF_GENERATE_CPP SRCS HDRS PROTOPATH)
  _protobuf_generate_cpp(SRCS2 HDRS2 ".pb" "--cpp_out" "" ${PROTOPATH} ${ARGN})
  set(${SRCS} ${SRCS2} PARENT_SCOPE)
  set(${HDRS} ${HDRS2} PARENT_SCOPE)
endfunction()

function(PROTOBUF_GENERATE_GRPC SRCS HDRS PROTOPATH)
  _protobuf_generate_cpp(SRCS2 HDRS2 ".grpc.pb" "--grpc_out" "protoc-gen-grpc=${GRPC_CPP_PLUGIN_PATH}" ${PROTOPATH} ${ARGN})
  set(${SRCS} ${SRCS2} PARENT_SCOPE)
  set(${HDRS} ${HDRS2} PARENT_SCOPE)
endfunction()
