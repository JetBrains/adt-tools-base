LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CPPFLAGS += -std=c++11

LOCAL_MODULE    := sample_client_on_device

LOCAL_SRC_FILES := client.cpp

include $(BUILD_EXECUTABLE)
