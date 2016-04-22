LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CPPFLAGS += -std=c++11
LOCAL_LDFLAGS += -fPIE -pie

LOCAL_MODULE    := network_sampler

LOCAL_SRC_FILES := ../network_data_collector.cc ../connection_data_collector.cc ../traffic_data_collector.cc test.cc

include $(BUILD_EXECUTABLE)
