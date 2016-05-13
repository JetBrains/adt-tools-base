LOCAL_PATH := $(call my-dir)

PROFILER_SERVER_PATH := $(LOCAL_PATH)/profiler_server
NETWORK_PATH := $(LOCAL_PATH)/network

# compile utils static library

include $(CLEAR_VARS)
LOCAL_MODULE := utils
# TODO: Check header files and move implementation to source file.
LOCAL_SRC_FILES := $(LOCAL_PATH)/utils/file_reader.cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/utils/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/utils/include
include $(BUILD_STATIC_LIBRARY)

# Compile profiler_server_network static library

include $(CLEAR_VARS)
LOCAL_MODULE := profiler_server_network

LOCAL_SRC_FILES := \
    $(NETWORK_PATH)/network_data_collector.cc \
    $(NETWORK_PATH)/traffic_data_collector.cc \
    $(NETWORK_PATH)/connection_data_collector.cc \
    $(NETWORK_PATH)/profiler_server_network.cc

# Add header files' paths to be included without path prefix.
LOCAL_C_INCLUDES := \
    $(NETWORK_PATH) \
    $(NETWORK_PATH)/include \
    $(PROFILER_SERVER_PATH)/common_include

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_STATIC_LIBRARIES := utils
LOCAL_CFLAGS := -DGOOGLE_PROTOBUF_NO_RTTI
include $(BUILD_STATIC_LIBRARY)

# Compile gmock into a static library

GMOCK_PATH := $(LOCAL_PATH)/../../../external/gmock
include $(CLEAR_VARS)
SRC_FILE_LIST           := $(GMOCK_PATH)/src/gmock-all.cc
LOCAL_MODULE            := googlemock
LOCAL_SRC_FILES         := $(SRC_FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_STATIC_LIBRARIES  := googletest_static
LOCAL_C_INCLUDES        := $(GMOCK_PATH) $(GMOCK_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(GMOCK_PATH)/include
include $(BUILD_STATIC_LIBRARY)

# Search given directory recursively and return files ended with _unittest.cc

define all-test-files-under
  $(filter %_unittest.cc, $(wildcard $(1))) \
  $(foreach d, $(wildcard $(1)/*), $(call all-test-files-under,$(d)))
endef

# Compile all test files into executable

include $(CLEAR_VARS)
LOCAL_MODULE           := unittests
SRC_FILE_LIST          := $(call all-test-files-under,$(LOCAL_PATH)/tests)
LOCAL_SRC_FILES        := $(SRC_FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_C_INCLUDES       := $(LOCAL_PATH)/utils
LOCAL_CPPFLAGS         += -std=c++11
LOCAL_STATIC_LIBRARIES := googletest_main googlemock utils
LOCAL_LDFLAGS          += -fPIE -pie
include $(BUILD_EXECUTABLE)
$(call import-module,third_party/googletest)
