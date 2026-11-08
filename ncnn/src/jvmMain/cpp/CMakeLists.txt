cmake_minimum_required(VERSION 3.20)

project(ncnn_jni LANGUAGES C CXX)

if(NOT NCNN_INSTALL_DIR)
    message(FATAL_ERROR "NCNN_INSTALL_DIR must point at an ncnn install prefix")
endif()

add_library(ncnn_jni SHARED ncnn_jni.c)

target_include_directories(ncnn_jni PRIVATE "${NCNN_INSTALL_DIR}/include/ncnn")

if(ANDROID)
    # jni.h is provided by the NDK sysroot.
    set_target_properties(ncnn_jni PROPERTIES LINKER_LANGUAGE CXX)
else()
    if(NOT JAVA_HOME)
        message(FATAL_ERROR "JAVA_HOME must point at a JDK")
    endif()
    target_include_directories(ncnn_jni PRIVATE "${JAVA_HOME}/include")
    if(WIN32)
        target_include_directories(ncnn_jni PRIVATE "${JAVA_HOME}/include/win32")
    elseif(APPLE)
        target_include_directories(ncnn_jni PRIVATE "${JAVA_HOME}/include/darwin")
    else()
        target_include_directories(ncnn_jni PRIVATE "${JAVA_HOME}/include/linux")
    endif()
    set_target_properties(ncnn_jni PROPERTIES LINKER_LANGUAGE CXX)
endif()

if(ANDROID)
    # ncnn's platform.h logs through __android_log_print on Android.
    target_link_libraries(ncnn_jni PRIVATE log)
endif()

if(WIN32 AND EXISTS "${NCNN_INSTALL_DIR}/lib/libncnn.dll.a")
    target_link_libraries(ncnn_jni PRIVATE "${NCNN_INSTALL_DIR}/lib/libncnn.dll.a")
elseif(UNIX AND NOT APPLE AND EXISTS "${NCNN_INSTALL_DIR}/lib/libncnn.so")
    target_link_libraries(ncnn_jni PRIVATE "${NCNN_INSTALL_DIR}/lib/libncnn.so")
else()
    target_link_libraries(ncnn_jni PRIVATE "${NCNN_INSTALL_DIR}/lib/libncnn.a")
endif()

if(ANDROID)
    # ANDROID_STL=c++_static is supplied by the NDK toolchain; no GNU runtime flags.
    target_link_options(ncnn_jni PRIVATE -Wl,--no-undefined)
elseif(WIN32)
    target_link_options(ncnn_jni PRIVATE -static-libgcc -static-libstdc++)
    # System.loadLibrary("ncnn_jni") looks for ncnn_jni.dll, not libncnn_jni.dll.
    set_target_properties(ncnn_jni PROPERTIES PREFIX "")
elseif(APPLE)
    target_link_options(ncnn_jni PRIVATE -lc++)
else()
    target_link_options(ncnn_jni PRIVATE -static-libgcc -static-libstdc++)
endif()

install(TARGETS ncnn_jni
    RUNTIME DESTINATION lib
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
