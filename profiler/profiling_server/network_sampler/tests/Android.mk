LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := network_sampler_unittest
LOCAL_CPPFLAGS += -std=c++11
LOCAL_LDFLAGS += -fPIE -pie

LOCAL_SRC_FILES := \
    time_value_buffer_unittest.cc \
    timespec_math_unittest.cc

LOCAL_STATIC_LIBRARIES := googletest_main

include $(BUILD_EXECUTABLE)

$(call import-module,third_party/googletest)
