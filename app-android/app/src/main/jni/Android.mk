LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := xd3jni
LOCAL_SRC_FILES := xd3jni.c
LOCAL_CFLAGS    := -O2 -DNDEBUG
LOCAL_LDLIBS    := -llog
include $(BUILD_SHARED_LIBRARY)
