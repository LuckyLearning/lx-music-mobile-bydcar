cmake_minimum_required(VERSION 3.18)
project(lxgate C)
if(NOT DEFINED QUICKJS_DIR)
    if(DEFINED ENV{QUICKJS_DIR})
        set(QUICKJS_DIR $ENV{QUICKJS_DIR})
    else()
        message(FATAL_ERROR "QUICKJS_DIR is required")
    endif()
endif()
add_library(lxgate SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../runtime/jni_gate.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../runtime/android_session.c
    ${QUICKJS_DIR}/quickjs.c
    ${QUICKJS_DIR}/dtoa.c
    ${QUICKJS_DIR}/libregexp.c
    ${QUICKJS_DIR}/libunicode.c
    ${QUICKJS_DIR}/cutils.c
)
target_include_directories(lxgate PRIVATE ${QUICKJS_DIR})
target_compile_definitions(lxgate PRIVATE CONFIG_VERSION="2026-06-04")
target_link_libraries(lxgate m log)
if(ANDROID)
    # Keep the library on the API the test app declares. Do not raise it here.
    target_compile_options(lxgate PRIVATE -Wall -Wno-unused-function)
endif()
