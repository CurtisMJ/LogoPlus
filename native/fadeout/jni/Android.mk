
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := fadeout
LOCAL_SRC_FILES:= \
fadeout.c

LOCAL_C_INCLUDES := bionic
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_MODULE := stream
LOCAL_SRC_FILES:= \
stream.c

LOCAL_C_INCLUDES := bionic
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
include $(BUILD_EXECUTABLE)
