cmake_minimum_required(VERSION 3.10)
project("paddle_ocr_jni")

set(CMAKE_CXX_STANDARD 17)

# Paddle Lite headers
include_directories(${CMAKE_SOURCE_DIR})

# Source files
add_library(paddle_ocr_jni SHARED
    native_ocr_jni.cpp
    rec_wrapper.cc
)

# Paddle Lite shared library
# Located at jniLibs/arm64-v8a/libpaddle_light_api_shared.so
set(PADDLE_LITE_LIB ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so)

target_link_libraries(paddle_ocr_jni
    ${PADDLE_LITE_LIB}
    log
    jnigraphics
)

# Fix Paddle Lite v2.14 .so compatibility with NDK linker
target_link_options(paddle_ocr_jni PRIVATE
    -Wl,--allow-shlib-undefined
)
