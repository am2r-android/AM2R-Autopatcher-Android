package com.community.am2r.patcher;

final class Xd3 {
    // Load the native decoder before PatchJob calls the Java-to-native entry point.
    static {
        System.loadLibrary("xd3jni");
    }

    /** Decode delta (src + delta -> dst). Returns 0 on success. */
    static native int decode(String srcPath, String deltaPath, String dstPath, long expectedSize);

    private Xd3() {}
}
