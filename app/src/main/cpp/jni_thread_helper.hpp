#pragma once

#include <jni.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include <thread>

#define LOG_TAG "NativeDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace native_detector {

/**
 * RAII wrapper for JNI thread attachment
 * Ensures proper thread lifecycle management
 */
class JNIThreadHelper {
private:
    JavaVM* jvm_;
    JNIEnv* env_;
    bool attached_;
    
public:
    explicit JNIThreadHelper(JavaVM* jvm) : jvm_(jvm), env_(nullptr), attached_(false) {
        if (jvm_) {
            jint result = jvm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                result = jvm_->AttachCurrentThread(&env_, nullptr);
                if (result == JNI_OK) {
                    attached_ = true;
                    LOGI("Thread attached to JVM");
                } else {
                    LOGE("Failed to attach thread to JVM: %d", result);
                }
            }
        }
    }
    
    ~JNIThreadHelper() {
        if (attached_ && jvm_) {
            jvm_->DetachCurrentThread();
            LOGI("Thread detached from JVM");
        }
    }
    
    JNIEnv* getEnv() const { return env_; }
    bool isValid() const { return env_ != nullptr; }
};

/**
 * Thread-safe native detector wrapper
 */
class ThreadSafeDetector {
private:
    std::mutex detector_mutex_;
    JavaVM* jvm_;
    
public:
    explicit ThreadSafeDetector(JavaVM* jvm) : jvm_(jvm) {
        LOGI("ThreadSafeDetector initialized");
    }
    
    /**
     * Perform detection with proper thread management
     */
    template<typename DetectorType>
    auto detectWithThreadSafety(DetectorType& detector, const cv::Mat& frame) {
        std::lock_guard<std::mutex> lock(detector_mutex_);
        
        // Ensure thread is attached to JVM
        JNIThreadHelper jni_helper(jvm_);
        if (!jni_helper.isValid()) {
            LOGE("Failed to attach thread for detection");
            return typename DetectorType::ResultType{};
        }
        
        try {
            return detector.detect(frame);
        } catch (const std::exception& e) {
            LOGE("Detection failed: %s", e.what());
            return typename DetectorType::ResultType{};
        }
    }
};

// Global JVM pointer for thread management
extern JavaVM* g_jvm;

} // namespace native_detector

// JNI exports for thread management
extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    native_detector::g_jvm = vm;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Native library loaded, JVM stored");
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_example_augmented_1mobile_1application_utils_JNIThreadManager_attachCurrentThread(
    JNIEnv* env, jclass clazz) {
    
    if (!native_detector::g_jvm) {
        LOGE("JVM not available for thread attachment");
        return JNI_FALSE;
    }
    
    JNIEnv* thread_env = nullptr;
    jint result = native_detector::g_jvm->GetEnv(reinterpret_cast<void**>(&thread_env), JNI_VERSION_1_6);
    
    if (result == JNI_EDETACHED) {
        result = native_detector::g_jvm->AttachCurrentThread(&thread_env, nullptr);
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
    
    if (native_detector::g_jvm) {
        native_detector::g_jvm->DetachCurrentThread();
        LOGI("Thread detached from JVM");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_augmented_1mobile_1application_utils_JNIThreadManager_isCurrentThreadAttached(
    JNIEnv* env, jclass clazz) {
    
    if (!native_detector::g_jvm) {
        return JNI_FALSE;
    }
    
    JNIEnv* thread_env = nullptr;
    jint result = native_detector::g_jvm->GetEnv(reinterpret_cast<void**>(&thread_env), JNI_VERSION_1_6);
    return (result == JNI_OK) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
