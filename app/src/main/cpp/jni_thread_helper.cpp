#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativeDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global JVM pointer for thread management
JavaVM* g_jvm = nullptr;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("Native library loaded, JVM stored");
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_example_augmented_1mobile_1application_utils_JNIThreadManager_attachCurrentThread(
    JNIEnv* env, jclass clazz) {
    
    if (!g_jvm) {
        LOGE("JVM not available for thread attachment");
        return JNI_FALSE;
    }
    
    JNIEnv* thread_env = nullptr;
    jint result = g_jvm->GetEnv(reinterpret_cast<void**>(&thread_env), JNI_VERSION_1_6);
    
    if (result == JNI_EDETACHED) {
        result = g_jvm->AttachCurrentThread(&thread_env, nullptr);
        if (result == JNI_OK) {
            LOGI("Thread successfully attached to JVM");
            return JNI_TRUE;
        } else {
            LOGE("Failed to attach thread: %d", result);
            return JNI_FALSE;
        }
    }
    
    return JNI_TRUE; // Already attached
}

JNIEXPORT void JNICALL
Java_com_example_augmented_1mobile_1application_utils_JNIThreadManager_detachCurrentThread(
    JNIEnv* env, jclass clazz) {
    
    if (g_jvm) {
        g_jvm->DetachCurrentThread();
        LOGI("Thread detached from JVM");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_augmented_1mobile_1application_utils_JNIThreadManager_isCurrentThreadAttached(
    JNIEnv* env, jclass clazz) {
    
    if (!g_jvm) {
        return JNI_FALSE;
    }
    
    JNIEnv* thread_env = nullptr;
    jint result = g_jvm->GetEnv(reinterpret_cast<void**>(&thread_env), JNI_VERSION_1_6);
    return (result == JNI_OK) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
