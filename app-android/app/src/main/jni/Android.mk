LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := xd3jni
LOCAL_SRC_FILES := xd3jni.c
LOCAL_CFLAGS    := -O2 -DNDEBUG
LOCAL_LDFLAGS   += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS    := -llog
include $(BUILD_SHARED_LIBRARY)
