/*
 * JNI bridge for the ncnn C API.
 *
 * Handles are passed as jlong values. Every ncnn failure is translated into
 * io.github.qingshu.ncnn.NcnnException. This file is compiled for desktop JVM
 * and for Android (NDK).
 */

#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "c_api.h"

static ncnn_net_t as_net(jlong handle)
{
    return (ncnn_net_t)(intptr_t)handle;
}

static ncnn_extractor_t as_extractor(jlong handle)
{
    return (ncnn_extractor_t)(intptr_t)handle;
}

static ncnn_mat_t as_mat(jlong handle)
{
    return (ncnn_mat_t)(intptr_t)handle;
}

static jlong to_handle(const void* pointer)
{
    return (jlong)(intptr_t)pointer;
}

static void throw_new(JNIEnv* env, const char* class_name, const char* message)
{
    jclass cls = (*env)->FindClass(env, class_name);
    if (cls)
        (*env)->ThrowNew(env, cls, message);
}

static void throw_ncnn(JNIEnv* env, const char* operation, int code)
{
    char message[256];
    snprintf(message, sizeof(message), "%s failed with ncnn status %d", operation, code);

    jclass cls = (*env)->FindClass(env, "io/github/qingshu/ncnn/NcnnException");
    if (!cls)
        return;
    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>", "(Ljava/lang/String;I)V");
    if (!ctor)
        return;
    jstring jmessage = (*env)->NewStringUTF(env, message);
    if (!jmessage)
        return;
    jobject exception = (*env)->NewObject(env, cls, ctor, jmessage, (jint)code);
    if (exception)
        (*env)->Throw(env, exception);
}

static void throw_out_of_memory(JNIEnv* env, const char* operation)
{
    char message[256];
    snprintf(message, sizeof(message), "%s failed: ncnn could not allocate the tensor", operation);
    throw_new(env, "java/lang/OutOfMemoryError", message);
}

/*
 * ncnn's DataReaderFromMemory hands out references into the model buffer for fp32
 * weights, so the buffer must stay alive as long as the net keeps those Mats.
 * Keep one buffer per net and release it on clear/destroy.
 */
typedef struct net_model_buffer
{
    ncnn_net_t net;
    unsigned char* data;
    struct net_model_buffer* next;
} net_model_buffer;

static net_model_buffer* g_model_buffers = NULL;

static void release_net_buffer(ncnn_net_t net)
{
    net_model_buffer** link = &g_model_buffers;
    while (*link)
    {
        if ((*link)->net == net)
        {
            net_model_buffer* entry = *link;
            *link = entry->next;
            free(entry->data);
            free(entry);
            return;
        }
        link = &(*link)->next;
    }
}

static void store_net_buffer(ncnn_net_t net, unsigned char* data)
{
    release_net_buffer(net);
    net_model_buffer* entry = (net_model_buffer*)malloc(sizeof(net_model_buffer));
    if (!entry)
    {
        free(data);
        return;
    }
    entry->net = net;
    entry->data = data;
    entry->next = g_model_buffers;
    g_model_buffers = entry;
}

/* UTF-16 (Java) -> UTF-8, including surrogate pairs. Caller frees. */
static char* jstring_to_utf8(JNIEnv* env, jstring value)
{
    if (!value)
        return NULL;

    jsize length = (*env)->GetStringLength(env, value);
    const jchar* chars = (*env)->GetStringChars(env, value, NULL);
    if (!chars)
        return NULL;

    char* out = (char*)malloc((size_t)length * 4 + 1);
    if (!out)
    {
        (*env)->ReleaseStringChars(env, value, chars);
        return NULL;
    }

    size_t o = 0;
    for (jsize i = 0; i < length; i++)
    {
        uint32_t c = chars[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < length)
        {
            uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF)
            {
                c = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
                i++;
            }
        }

        if (c < 0x80)
        {
            out[o++] = (char)c;
        }
        else if (c < 0x800)
        {
            out[o++] = (char)(0xC0 | (c >> 6));
            out[o++] = (char)(0x80 | (c & 0x3F));
        }
        else if (c < 0x10000)
        {
            out[o++] = (char)(0xE0 | (c >> 12));
            out[o++] = (char)(0x80 | ((c >> 6) & 0x3F));
            out[o++] = (char)(0x80 | (c & 0x3F));
        }
        else
        {
            out[o++] = (char)(0xF0 | (c >> 18));
            out[o++] = (char)(0x80 | ((c >> 12) & 0x3F));
            out[o++] = (char)(0x80 | ((c >> 6) & 0x3F));
            out[o++] = (char)(0x80 | (c & 0x3F));
        }
    }
    out[o] = 0;

    (*env)->ReleaseStringChars(env, value, chars);
    return out;
}

#ifdef _WIN32
/* UTF-16 (Java) -> wchar_t (16 bit on Windows). Caller frees. */
static wchar_t* jstring_to_wide(JNIEnv* env, jstring value)
{
    if (!value)
        return NULL;

    jsize length = (*env)->GetStringLength(env, value);
    const jchar* chars = (*env)->GetStringChars(env, value, NULL);
    if (!chars)
        return NULL;

    wchar_t* out = (wchar_t*)malloc(((size_t)length + 1) * sizeof(wchar_t));
    if (!out)
    {
        (*env)->ReleaseStringChars(env, value, chars);
        return NULL;
    }
    memcpy(out, chars, (size_t)length * sizeof(wchar_t));
    out[length] = 0;
    (*env)->ReleaseStringChars(env, value, chars);
    return out;
}
#endif

static int load_param_path(JNIEnv* env, jlong handle, jstring path)
{
#ifdef _WIN32
    wchar_t* wide = jstring_to_wide(env, path);
    if (!wide)
        return -1;
    int ret = ncnn_net_load_param_w(as_net(handle), wide);
    free(wide);
    return ret;
#else
    char* utf8 = jstring_to_utf8(env, path);
    if (!utf8)
        return -1;
    int ret = ncnn_net_load_param(as_net(handle), utf8);
    free(utf8);
    return ret;
#endif
}

static int load_model_path(JNIEnv* env, jlong handle, jstring path)
{
#ifdef _WIN32
    wchar_t* wide = jstring_to_wide(env, path);
    if (!wide)
        return -1;
    int ret = ncnn_net_load_model_w(as_net(handle), wide);
    free(wide);
    return ret;
#else
    char* utf8 = jstring_to_utf8(env, path);
    if (!utf8)
        return -1;
    int ret = ncnn_net_load_model(as_net(handle), utf8);
    free(utf8);
    return ret;
#endif
}

static jlong require_mat(JNIEnv* env, ncnn_mat_t mat, const char* operation)
{
    if (!mat)
    {
        throw_ncnn(env, operation, -1);
        return 0;
    }
    return to_handle(mat);
}

/*
 * ncnn's Mat::cstep is measured in elements (not bytes) and includes per-channel
 * alignment padding. dataSize is the logical data size the user sees, without padding.
 */
static jlong mat_data_size(ncnn_mat_t mat)
{
    int w = ncnn_mat_get_w(mat);
    int h = ncnn_mat_get_h(mat);
    int d = ncnn_mat_get_d(mat);
    int c = ncnn_mat_get_c(mat);
    int n = ncnn_mat_get_n(mat);
    size_t elemsize = ncnn_mat_get_elemsize(mat);
    if (h <= 0)
        h = 1;
    if (d <= 0)
        d = 1;
    if (c <= 0)
        c = 1;
    if (n <= 0)
        n = 1;
    return (jlong)((size_t)w * h * d * c * n * elemsize);
}

static size_t mat_per_channel_elems(ncnn_mat_t mat)
{
    int w = ncnn_mat_get_w(mat);
    int h = ncnn_mat_get_h(mat);
    int d = ncnn_mat_get_d(mat);
    if (h <= 0)
        h = 1;
    if (d <= 0)
        d = 1;
    return (size_t)w * h * d;
}

static size_t mat_channel_count(ncnn_mat_t mat)
{
    int c = ncnn_mat_get_c(mat);
    int n = ncnn_mat_get_n(mat);
    if (c <= 0)
        c = 1;
    if (n <= 0)
        n = 1;
    return (size_t)c * n;
}

static int pixel_channels(int type)
{
    switch (type)
    {
    case 1: /* RGB */
    case 2: /* BGR */
        return 3;
    case 3: /* GRAY */
        return 1;
    case 4: /* RGBA */
    case 5: /* BGRA */
        return 4;
    default:
        return 0;
    }
}

JNIEXPORT jstring JNICALL Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionString(JNIEnv* env, jclass clazz)
{
    (void)clazz;
    return (*env)->NewStringUTF(env, ncnn_version());
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionNumberJni(JNIEnv* env, jclass clazz)
{
    (void)env;
    (void)clazz;
    return (jint)ncnn_version_number();
}

/* ------------------------------------------------------------------ net */

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netCreate(JNIEnv* env, jclass clazz)
{
    (void)clazz;
    ncnn_net_t net = ncnn_net_create();
    if (!net)
    {
        throw_out_of_memory(env, "Net.create");
        return 0;
    }
    return to_handle(net);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netDestroy(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    if (handle)
    {
        ncnn_net_t net = as_net(handle);
        ncnn_net_destroy(net);
        release_net_buffer(net);
    }
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netGetOptionInt(JNIEnv* env, jclass clazz, jlong handle, jint option)
{
    (void)env;
    (void)clazz;
    ncnn_option_t opt = ncnn_net_get_option(as_net(handle));
    switch (option)
    {
    case 0:
        return ncnn_option_get_num_threads(opt);
    case 1:
        return ncnn_option_get_use_local_pool_allocator(opt);
    case 2:
        return ncnn_option_get_use_winograd_convolution(opt);
    case 3:
        return ncnn_option_get_use_sgemm_convolution(opt);
    case 4:
        return ncnn_option_get_use_packing_layout(opt);
    case 5:
        return ncnn_option_get_use_fp16_packed(opt);
    case 6:
        return ncnn_option_get_use_fp16_storage(opt);
    case 7:
        return ncnn_option_get_use_fp16_arithmetic(opt);
    case 8:
        return ncnn_option_get_use_int8_packed(opt);
    case 9:
        return ncnn_option_get_use_int8_storage(opt);
    case 10:
        return ncnn_option_get_use_int8_arithmetic(opt);
    case 11:
        return ncnn_option_get_use_int16_packed(opt);
    case 12:
        return ncnn_option_get_use_int16_storage(opt);
    case 13:
        return ncnn_option_get_use_bf16_packed(opt);
    case 14:
        return ncnn_option_get_use_bf16_storage(opt);
    default:
        return 0;
    }
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netSetOptionInt(JNIEnv* env, jclass clazz, jlong handle, jint option, jint value)
{
    (void)env;
    (void)clazz;
    ncnn_option_t opt = ncnn_net_get_option(as_net(handle));
    switch (option)
    {
    case 0:
        ncnn_option_set_num_threads(opt, value);
        break;
    case 1:
        ncnn_option_set_use_local_pool_allocator(opt, value);
        break;
    case 2:
        ncnn_option_set_use_winograd_convolution(opt, value);
        break;
    case 3:
        ncnn_option_set_use_sgemm_convolution(opt, value);
        break;
    case 4:
        ncnn_option_set_use_packing_layout(opt, value);
        break;
    case 5:
        ncnn_option_set_use_fp16_packed(opt, value);
        break;
    case 6:
        ncnn_option_set_use_fp16_storage(opt, value);
        break;
    case 7:
        ncnn_option_set_use_fp16_arithmetic(opt, value);
        break;
    case 8:
        ncnn_option_set_use_int8_packed(opt, value);
        break;
    case 9:
        ncnn_option_set_use_int8_storage(opt, value);
        break;
    case 10:
        ncnn_option_set_use_int8_arithmetic(opt, value);
        break;
    case 11:
        ncnn_option_set_use_int16_packed(opt, value);
        break;
    case 12:
        ncnn_option_set_use_int16_storage(opt, value);
        break;
    case 13:
        ncnn_option_set_use_bf16_packed(opt, value);
        break;
    case 14:
        ncnn_option_set_use_bf16_storage(opt, value);
        break;
    default:
        throw_new(env, "java/lang/IllegalArgumentException", "unknown ncnn option id");
        break;
    }
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamPath(JNIEnv* env, jclass clazz, jlong handle, jstring path)
{
    (void)clazz;
    int ret = load_param_path(env, handle, path);
    if (ret != 0)
        throw_ncnn(env, "Net.loadParam(path)", ret);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamMemory(JNIEnv* env, jclass clazz, jlong handle, jbyteArray data)
{
    (void)clazz;
    jsize length = (*env)->GetArrayLength(env, data);
    char* buffer = (char*)malloc((size_t)length + 1);
    if (!buffer)
    {
        throw_out_of_memory(env, "Net.loadParam(memory)");
        return;
    }
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte*)buffer);
    buffer[length] = 0;

    int ret = ncnn_net_load_param_memory(as_net(handle), buffer);
    free(buffer);
    if (ret != 0)
        throw_ncnn(env, "Net.loadParam(memory)", ret);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelPath(JNIEnv* env, jclass clazz, jlong handle, jstring path)
{
    (void)clazz;
    int ret = load_model_path(env, handle, path);
    if (ret != 0)
        throw_ncnn(env, "Net.loadModel(path)", ret);
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelMemory(JNIEnv* env, jclass clazz, jlong handle, jbyteArray data)
{
    (void)clazz;
    ncnn_net_t net = as_net(handle);
    jsize length = (*env)->GetArrayLength(env, data);
    if (length == 0)
    {
        static const unsigned char dummy = 0;
        return (jlong)ncnn_net_load_model_memory(net, &dummy);
    }

    unsigned char* buffer = (unsigned char*)malloc((size_t)length);
    if (!buffer)
    {
        throw_out_of_memory(env, "Net.loadModel(memory)");
        return 0;
    }
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte*)buffer);

    jlong consumed = (jlong)ncnn_net_load_model_memory(net, buffer);
    if (consumed == 0)
    {
        free(buffer);
        throw_ncnn(env, "Net.loadModel(memory)", -1);
        return 0;
    }

    store_net_buffer(net, buffer);
    return consumed;
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netInputCount(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_net_get_input_count(as_net(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netOutputCount(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_net_get_output_count(as_net(handle));
}

JNIEXPORT jstring JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netInputName(JNIEnv* env, jclass clazz, jlong handle, jint index)
{
    (void)clazz;
    const char* name = ncnn_net_get_input_name(as_net(handle), index);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jstring JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netOutputName(JNIEnv* env, jclass clazz, jlong handle, jint index)
{
    (void)clazz;
    const char* name = ncnn_net_get_output_name(as_net(handle), index);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netInputIndex(JNIEnv* env, jclass clazz, jlong handle, jint index)
{
    (void)env;
    (void)clazz;
    return ncnn_net_get_input_index(as_net(handle), index);
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netOutputIndex(JNIEnv* env, jclass clazz, jlong handle, jint index)
{
    (void)env;
    (void)clazz;
    return ncnn_net_get_output_index(as_net(handle), index);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_netClear(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    ncnn_net_t net = as_net(handle);
    ncnn_net_clear(net);
    release_net_buffer(net);
}

/* ------------------------------------------------------------ extractor */

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorCreate(JNIEnv* env, jclass clazz, jlong net)
{
    (void)clazz;
    ncnn_extractor_t extractor = ncnn_extractor_create(as_net(net));
    if (!extractor)
    {
        throw_out_of_memory(env, "Net.createExtractor");
        return 0;
    }
    return to_handle(extractor);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorDestroy(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    if (handle)
        ncnn_extractor_destroy(as_extractor(handle));
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorInputName(JNIEnv* env, jclass clazz, jlong handle, jstring name, jlong mat)
{
    (void)clazz;
    char* utf8 = jstring_to_utf8(env, name);
    if (!utf8)
        return;
    int ret = ncnn_extractor_input(as_extractor(handle), utf8, as_mat(mat));
    free(utf8);
    if (ret != 0)
        throw_ncnn(env, "Extractor.input(name)", ret);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorInputIndex(JNIEnv* env, jclass clazz, jlong handle, jint index, jlong mat)
{
    (void)clazz;
    int ret = ncnn_extractor_input_index(as_extractor(handle), index, as_mat(mat));
    if (ret != 0)
        throw_ncnn(env, "Extractor.input(index)", ret);
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractName(JNIEnv* env, jclass clazz, jlong handle, jstring name)
{
    (void)clazz;
    char* utf8 = jstring_to_utf8(env, name);
    if (!utf8)
        return 0;

    ncnn_mat_t output = NULL;
    int ret = ncnn_extractor_extract(as_extractor(handle), utf8, &output);
    free(utf8);
    if (ret != 0)
    {
        if (output)
            ncnn_mat_destroy(output);
        throw_ncnn(env, "Extractor.extract(name)", ret);
        return 0;
    }
    return require_mat(env, output, "Extractor.extract(name)");
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractIndex(JNIEnv* env, jclass clazz, jlong handle, jint index)
{
    (void)clazz;
    ncnn_mat_t output = NULL;
    int ret = ncnn_extractor_extract_index(as_extractor(handle), index, &output);
    if (ret != 0)
    {
        if (output)
            ncnn_mat_destroy(output);
        throw_ncnn(env, "Extractor.extract(index)", ret);
        return 0;
    }
    return require_mat(env, output, "Extractor.extract(index)");
}

/* ------------------------------------------------------------------ mat */

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matCreate(JNIEnv* env, jclass clazz, jint w, jint h, jint c)
{
    (void)clazz;
    ncnn_mat_t mat = NULL;
    if (c > 0)
        mat = ncnn_mat_create_3d(w, h, c, NULL);
    else if (h > 0)
        mat = ncnn_mat_create_2d(w, h, NULL);
    else
        mat = ncnn_mat_create_1d(w, NULL);

    if (!mat)
    {
        throw_out_of_memory(env, "Mat.create");
        return 0;
    }
    if (!ncnn_mat_get_data(mat) && ncnn_mat_get_cstep(mat) > 0)
    {
        ncnn_mat_destroy(mat);
        throw_out_of_memory(env, "Mat.create");
        return 0;
    }
    return to_handle(mat);
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matDestroy(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    if (handle)
        ncnn_mat_destroy(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matDims(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_dims(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matW(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_w(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matH(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_h(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matD(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_d(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matC(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_c(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matN(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_n(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matElementSize(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return (jint)ncnn_mat_get_elemsize(as_mat(handle));
}

JNIEXPORT jint JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matElementPack(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return ncnn_mat_get_elempack(as_mat(handle));
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matChannelStep(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return (jlong)ncnn_mat_get_cstep(as_mat(handle));
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matDataSize(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return mat_data_size(as_mat(handle));
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matFill(JNIEnv* env, jclass clazz, jlong handle, jfloat value)
{
    (void)env;
    (void)clazz;
    ncnn_mat_fill_float(as_mat(handle), value);
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matClone(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)clazz;
    return require_mat(env, ncnn_mat_clone(as_mat(handle), NULL), "Mat.clone");
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matReshape(JNIEnv* env, jclass clazz, jlong handle, jint w, jint h, jint c)
{
    (void)clazz;
    ncnn_mat_t out = NULL;
    if (c > 0)
        out = ncnn_mat_reshape_3d(as_mat(handle), w, h, c, NULL);
    else if (h > 0)
        out = ncnn_mat_reshape_2d(as_mat(handle), w, h, NULL);
    else
        out = ncnn_mat_reshape_1d(as_mat(handle), w, NULL);
    return require_mat(env, out, "Mat.reshape");
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matConvertPacking(JNIEnv* env, jclass clazz, jlong handle, jint element_pack)
{
    (void)clazz;
    ncnn_mat_t out = NULL;
    ncnn_convert_packing(as_mat(handle), &out, element_pack, NULL);
    return require_mat(env, out, "Mat.convertPacking");
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matFlatten(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)clazz;
    ncnn_mat_t out = NULL;
    ncnn_flatten(as_mat(handle), &out, NULL);
    return require_mat(env, out, "Mat.flatten");
}

JNIEXPORT jfloatArray JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matReadFloats(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)clazz;
    ncnn_mat_t mat = as_mat(handle);
    if (ncnn_mat_get_elemsize(mat) != 4 || ncnn_mat_get_elempack(mat) != 1)
    {
        throw_new(env, "java/lang/IllegalStateException",
                  "Mat.readFloats requires fp32 pack1 data; use convertPacking(1) or disable fp16/bf16/int8 storage");
        return NULL;
    }

    size_t per_channel = mat_per_channel_elems(mat);
    size_t channels = mat_channel_count(mat);
    if (per_channel > 0x7FFFFFFFL || channels > 0x7FFFFFFFL / (per_channel ? per_channel : 1))
    {
        throw_new(env, "java/lang/IllegalStateException", "Mat is too large to expose as FloatArray");
        return NULL;
    }

    jsize count = (jsize)(per_channel * channels);
    jfloatArray result = (*env)->NewFloatArray(env, count);
    if (!result)
        return NULL;

    const float* data = (const float*)ncnn_mat_get_data(mat);
    if (data && count > 0)
    {
        size_t cstep = ncnn_mat_get_cstep(mat);
        for (size_t channel = 0; channel < channels; channel++)
        {
            if (per_channel > 0)
                (*env)->SetFloatArrayRegion(env, result, (jsize)(channel * per_channel),
                                            (jsize)per_channel, data + channel * cstep);
        }
    }
    return result;
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matWriteFloats(JNIEnv* env, jclass clazz, jlong handle, jfloatArray values)
{
    (void)clazz;
    ncnn_mat_t mat = as_mat(handle);
    if (ncnn_mat_get_elemsize(mat) != 4 || ncnn_mat_get_elempack(mat) != 1)
    {
        throw_new(env, "java/lang/IllegalStateException",
                  "Mat.writeFloats requires fp32 pack1 data; use convertPacking(1) or disable fp16/bf16/int8 storage");
        return;
    }

    size_t per_channel = mat_per_channel_elems(mat);
    size_t channels = mat_channel_count(mat);
    size_t total = per_channel * channels;
    jsize length = (*env)->GetArrayLength(env, values);
    if ((size_t)length > total)
    {
        throw_new(env, "java/lang/IllegalArgumentException", "source array is larger than the tensor");
        return;
    }

    float* data = (float*)ncnn_mat_get_data(mat);
    if (!data || length == 0)
        return;

    jfloat* source = (*env)->GetFloatArrayElements(env, values, NULL);
    if (!source)
        return;

    size_t cstep = ncnn_mat_get_cstep(mat);
    size_t copied = 0;
    for (size_t channel = 0; channel < channels && copied < (size_t)length; channel++)
    {
        size_t n = per_channel;
        if (n > (size_t)length - copied)
            n = (size_t)length - copied;
        memcpy(data + channel * cstep, source + copied, n * sizeof(float));
        copied += n;
    }
    (*env)->ReleaseFloatArrayElements(env, values, source, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matReadBytes(JNIEnv* env, jclass clazz, jlong handle)
{
    (void)clazz;
    ncnn_mat_t mat = as_mat(handle);
    size_t elemsize = ncnn_mat_get_elemsize(mat);
    size_t per_channel_bytes = mat_per_channel_elems(mat) * elemsize;
    size_t channels = mat_channel_count(mat);
    if (per_channel_bytes > 0x7FFFFFFFL || channels > 0x7FFFFFFFL / (per_channel_bytes ? per_channel_bytes : 1))
    {
        throw_new(env, "java/lang/IllegalStateException", "Mat is too large to expose as ByteArray");
        return NULL;
    }

    jsize size = (jsize)(per_channel_bytes * channels);
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (!result)
        return NULL;

    const unsigned char* data = (const unsigned char*)ncnn_mat_get_data(mat);
    if (data && size > 0)
    {
        size_t cstep_bytes = ncnn_mat_get_cstep(mat) * elemsize;
        for (size_t channel = 0; channel < channels; channel++)
        {
            if (per_channel_bytes > 0)
                (*env)->SetByteArrayRegion(env, result, (jsize)(channel * per_channel_bytes),
                                           (jsize)per_channel_bytes, (const jbyte*)(data + channel * cstep_bytes));
        }
    }
    return result;
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matWriteBytes(JNIEnv* env, jclass clazz, jlong handle, jbyteArray bytes)
{
    (void)clazz;
    ncnn_mat_t mat = as_mat(handle);
    size_t elemsize = ncnn_mat_get_elemsize(mat);
    size_t per_channel_bytes = mat_per_channel_elems(mat) * elemsize;
    size_t channels = mat_channel_count(mat);
    size_t total = per_channel_bytes * channels;
    jsize length = (*env)->GetArrayLength(env, bytes);
    if ((size_t)length > total)
    {
        throw_new(env, "java/lang/IllegalArgumentException", "source array is larger than the tensor");
        return;
    }

    unsigned char* data = (unsigned char*)ncnn_mat_get_data(mat);
    if (!data || length == 0)
        return;

    size_t cstep_bytes = ncnn_mat_get_cstep(mat) * elemsize;
    size_t copied = 0;
    for (size_t channel = 0; channel < channels && copied < (size_t)length; channel++)
    {
        size_t n = per_channel_bytes;
        if (n > (size_t)length - copied)
            n = (size_t)length - copied;
        (*env)->GetByteArrayRegion(env, bytes, (jsize)copied, (jsize)n,
                                   (jbyte*)(data + channel * cstep_bytes));
        copied += n;
    }
}

JNIEXPORT void JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matSubstractMeanNormalize(JNIEnv* env, jclass clazz, jlong handle, jfloatArray mean, jfloatArray norm)
{
    (void)clazz;
    jfloat* mean_values = mean ? (*env)->GetFloatArrayElements(env, mean, NULL) : NULL;
    jfloat* norm_values = norm ? (*env)->GetFloatArrayElements(env, norm, NULL) : NULL;

    ncnn_mat_substract_mean_normalize(as_mat(handle), mean_values, norm_values);

    if (mean && mean_values)
        (*env)->ReleaseFloatArrayElements(env, mean, mean_values, JNI_ABORT);
    if (norm && norm_values)
        (*env)->ReleaseFloatArrayElements(env, norm, norm_values, JNI_ABORT);
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matFromPixels(JNIEnv* env, jclass clazz, jbyteArray pixels, jint type, jint w, jint h, jint stride)
{
    (void)clazz;
    jbyte* buffer = (*env)->GetByteArrayElements(env, pixels, NULL);
    if (!buffer)
    {
        throw_out_of_memory(env, "Mat.fromPixels");
        return 0;
    }
    ncnn_mat_t mat = ncnn_mat_from_pixels((const unsigned char*)buffer, type, w, h, stride, NULL);
    (*env)->ReleaseByteArrayElements(env, pixels, buffer, JNI_ABORT);
    return require_mat(env, mat, "Mat.fromPixels");
}

JNIEXPORT jlong JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matFromPixelsResize(JNIEnv* env, jclass clazz, jbyteArray pixels, jint type, jint w, jint h, jint stride, jint target_w, jint target_h)
{
    (void)clazz;
    jbyte* buffer = (*env)->GetByteArrayElements(env, pixels, NULL);
    if (!buffer)
    {
        throw_out_of_memory(env, "Mat.fromPixelsResize");
        return 0;
    }
    ncnn_mat_t mat = ncnn_mat_from_pixels_resize((const unsigned char*)buffer, type, w, h, stride, target_w, target_h, NULL);
    (*env)->ReleaseByteArrayElements(env, pixels, buffer, JNI_ABORT);
    return require_mat(env, mat, "Mat.fromPixelsResize");
}

JNIEXPORT jbyteArray JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matToPixels(JNIEnv* env, jclass clazz, jlong handle, jint type, jint stride)
{
    (void)clazz;
    int channels = pixel_channels(type);
    if (channels == 0)
    {
        throw_new(env, "java/lang/IllegalArgumentException", "unsupported pixel type");
        return NULL;
    }
    ncnn_mat_t mat = as_mat(handle);
    int w = ncnn_mat_get_w(mat);
    int h = ncnn_mat_get_h(mat);
    if (stride <= 0)
        stride = w * channels;
    jlong size = (jlong)stride * h;
    if (size > 0x7FFFFFFFL)
    {
        throw_new(env, "java/lang/IllegalStateException", "pixel buffer would be too large");
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (!result)
        return NULL;
    jbyte* buffer = (*env)->GetByteArrayElements(env, result, NULL);
    if (!buffer)
        return NULL;
    ncnn_mat_to_pixels(mat, (unsigned char*)buffer, type, stride);
    (*env)->ReleaseByteArrayElements(env, result, buffer, 0);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_io_github_qingshu_ncnn_NcnnJni_matToPixelsResize(JNIEnv* env, jclass clazz, jlong handle, jint type, jint target_w, jint target_h, jint stride)
{
    (void)clazz;
    int channels = pixel_channels(type);
    if (channels == 0)
    {
        throw_new(env, "java/lang/IllegalArgumentException", "unsupported pixel type");
        return NULL;
    }
    if (stride <= 0)
        stride = target_w * channels;
    jlong size = (jlong)stride * target_h;
    if (size > 0x7FFFFFFFL)
    {
        throw_new(env, "java/lang/IllegalStateException", "pixel buffer would be too large");
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (!result)
        return NULL;
    jbyte* buffer = (*env)->GetByteArrayElements(env, result, NULL);
    if (!buffer)
        return NULL;
    ncnn_mat_to_pixels_resize(as_mat(handle), (unsigned char*)buffer, type, target_w, target_h, stride);
    (*env)->ReleaseByteArrayElements(env, result, buffer, 0);
    return result;
}
