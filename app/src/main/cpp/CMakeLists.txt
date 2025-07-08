# CMakeLists.txt for thread-safe JNI operations (simplified)
cmake_minimum_required(VERSION 3.18.1)

project("native-detector")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages (remove OpenCV for now)
find_library(log-lib log)

# Add library with just the JNI thread helper
add_library(
    native-detector
    SHARED
    jni_thread_helper.cpp  # Create implementation file instead of header-only
)

# Link libraries
target_link_libraries(
    native-detector
    ${log-lib}
    android
)

# Compiler flags for optimization and debugging
target_compile_options(native-detector PRIVATE
    -O3                    # Optimize for performance
    -DANDROID             # Android platform flag
    -fvisibility=hidden   # Hide symbols by default
    -Wall                 # Enable warnings
)

# Thread safety and JNI definitions
target_compile_definitions(native-detector PRIVATE
    USE_THREAD_SAFE_JNI=1
    ANDROID_NDK_VERSION=27
)
