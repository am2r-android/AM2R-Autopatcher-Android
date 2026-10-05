/* JNI wrapper around xdelta3's whole-buffer decoder.
 *
 * The patch data's droid.xdelta is produced with `xdelta3 -e -9 -S djw`,
 * so the DJW secondary decompressor must be compiled in.
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SIZEOF_SIZE_T 8
#define SIZEOF_UNSIGNED_INT 4
#define SIZEOF_UNSIGNED_LONG 8
#define SIZEOF_UNSIGNED_LONG_LONG 8
#define SECONDARY_DJW 1
#define SECONDARY_FGK 1
#define SECONDARY_LZMA 0
#define EXTERNAL_COMPRESSION 0
#define XD3_MAIN 0
#define XD3_ENCODER 1
#define XD3_POSIX 1
#define XD3_USE_LARGEFILE64 1

#include "xdelta3/xdelta3.c"

static uint8_t *read_all(const char *path, size_t *out_size)
{
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz < 0) { fclose(f); return NULL; }
    uint8_t *buf = malloc((size_t)sz);
    if (!buf) { fclose(f); return NULL; }
    if (fread(buf, 1, (size_t)sz, f) != (size_t)sz) { free(buf); fclose(f); return NULL; }
    fclose(f);
    *out_size = (size_t)sz;
    return buf;
}

/* Returns 0 on success, negative on failure. */
JNIEXPORT jint JNICALL
Java_com_community_am2r_patcher_Xd3_decode(JNIEnv *env, jclass cls,
                                           jstring jsrc, jstring jdelta,
                                           jstring jdst, jlong expected_size)
{
    const char *src_path = (*env)->GetStringUTFChars(env, jsrc, NULL);
    const char *delta_path = (*env)->GetStringUTFChars(env, jdelta, NULL);
    const char *dst_path = (*env)->GetStringUTFChars(env, jdst, NULL);

    int ret = -1;
    size_t src_size = 0, delta_size = 0;
    uint8_t *src = NULL, *delta = NULL, *out = NULL;
    usize_t out_size = 0;

    src = read_all(src_path, &src_size);
    if (!src) { ret = -2; goto done; }
    delta = read_all(delta_path, &delta_size);
    if (!delta) { ret = -3; goto done; }
    out = malloc((size_t)expected_size);
    if (!out) { ret = -4; goto done; }

    int r = xd3_decode_memory(delta, (usize_t)delta_size,
                              src, (usize_t)src_size,
                              out, &out_size, (usize_t)expected_size, 0);
    if (r != 0) { ret = -5; goto done; }
    if ((jlong)out_size != expected_size) { ret = -6; goto done; }

    FILE *f = fopen(dst_path, "wb");
    if (!f) { ret = -7; goto done; }
    if (fwrite(out, 1, out_size, f) != out_size) { fclose(f); ret = -8; goto done; }
    fclose(f);
    ret = 0;

done:
    free(src); free(delta); free(out);
    (*env)->ReleaseStringUTFChars(env, jsrc, src_path);
    (*env)->ReleaseStringUTFChars(env, jdelta, delta_path);
    (*env)->ReleaseStringUTFChars(env, jdst, dst_path);
    return ret;
}
