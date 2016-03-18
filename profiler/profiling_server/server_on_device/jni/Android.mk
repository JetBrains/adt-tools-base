LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CPPFLAGS += -std=c++11

LOCAL_MODULE    := studio_profiling_server

LOCAL_SRC_FILES := server.cpp
LOCAL_SRC_FILES += system_data_collector.cpp

include $(BUILD_EXECUTABLE)
