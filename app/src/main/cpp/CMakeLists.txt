cmake_minimum_required(VERSION 3.22.1)
project(whisperlm_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─────────────────────────────────────────────────────────────────────────────
# whisper.cpp source
# Expected at: src/main/cpp/whisper.cpp/
# Users must place the whisper.cpp source tree here (git clone whisper.cpp into that dir)
# ─────────────────────────────────────────────────────────────────────────────
set(WHISPER_CPP_DIR ${CMAKE_SOURCE_DIR}/whisper.cpp)

if(EXISTS ${WHISPER_CPP_DIR}/whisper.cpp)
    set(WHISPER_SOURCES
        ${WHISPER_CPP_DIR}/whisper.cpp
        ${WHISPER_CPP_DIR}/ggml.c
        ${WHISPER_CPP_DIR}/ggml-alloc.c
        ${WHISPER_CPP_DIR}/ggml-backend.c
        ${WHISPER_CPP_DIR}/ggml-quants.c
    )

    add_library(whisper STATIC ${WHISPER_SOURCES})
    target_include_directories(whisper PUBLIC ${WHISPER_CPP_DIR})
    target_compile_options(whisper PRIVATE -O3 -DNDEBUG)
    set(WHISPER_AVAILABLE TRUE)
else()
    message(STATUS "whisper.cpp not found at ${WHISPER_CPP_DIR} - using stub")
    set(WHISPER_AVAILABLE FALSE)
endif()

# ─────────────────────────────────────────────────────────────────────────────
# llama.cpp source (for LLM fallback)
# Expected at: src/main/cpp/llama.cpp/
# ─────────────────────────────────────────────────────────────────────────────
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

if(EXISTS ${LLAMA_CPP_DIR}/llama.cpp)
    add_subdirectory(${LLAMA_CPP_DIR} llama_build)
    set(LLAMA_AVAILABLE TRUE)
else()
    message(STATUS "llama.cpp not found at ${LLAMA_CPP_DIR} - using stub")
    set(LLAMA_AVAILABLE FALSE)
endif()

# ─────────────────────────────────────────────────────────────────────────────
# JNI Bridge library
# ─────────────────────────────────────────────────────────────────────────────
add_library(whisperlm_jni SHARED
    whisper_jni.cpp
    llm_jni.cpp
    audio_utils.cpp
)

target_include_directories(whisperlm_jni PRIVATE
    ${CMAKE_SOURCE_DIR}
)

if(WHISPER_AVAILABLE)
    target_include_directories(whisperlm_jni PRIVATE ${WHISPER_CPP_DIR})
    target_link_libraries(whisperlm_jni whisper)
    target_compile_definitions(whisperlm_jni PRIVATE WHISPER_AVAILABLE=1)
endif()

if(LLAMA_AVAILABLE)
    target_link_libraries(whisperlm_jni llama)
    target_compile_definitions(whisperlm_jni PRIVATE LLAMA_AVAILABLE=1)
endif()

# Android system libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(whisperlm_jni
    ${log-lib}
    ${android-lib}
)

target_compile_options(whisperlm_jni PRIVATE -O3 -ffast-math)
