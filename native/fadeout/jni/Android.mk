
my_path3 := $(call my-dir)

LOCAL_PATH:= $(my_path3)

include $(CLEAR_VARS)

LOCAL_MODULE := fadeout
LOCAL_SRC_FILES:= \
fadeout.c

LOCAL_C_INCLUDES := bionic
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
include $(BUILD_EXECUTABLE)
