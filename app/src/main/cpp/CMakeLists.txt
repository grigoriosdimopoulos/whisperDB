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

if(EXISTS ${WHISPER_CPP_DIR}/CMakeLists.txt)
    # Disable unused components for Android build
    set(WHISPER_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
    set(GGML_METAL              OFF CACHE BOOL "" FORCE)
    set(GGML_CUDA               OFF CACHE BOOL "" FORCE)
    set(GGML_VULKAN             OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS       OFF CACHE BOOL "" FORCE)

    add_subdirectory(${WHISPER_CPP_DIR} whisper_build)
    set(WHISPER_AVAILABLE TRUE)

    # whisper.h lives in include/ on newer versions, root on older ones
    if(EXISTS ${WHISPER_CPP_DIR}/include/whisper.h)
        set(WHISPER_INCLUDE_DIR ${WHISPER_CPP_DIR}/include)
    else()
        set(WHISPER_INCLUDE_DIR ${WHISPER_CPP_DIR})
    endif()
    message(STATUS "whisper.cpp found — building with STT support")
else()
    message(STATUS "whisper.cpp not found at ${WHISPER_CPP_DIR} - using stub")
    set(WHISPER_AVAILABLE FALSE)
endif()

# ─────────────────────────────────────────────────────────────────────────────
# llama.cpp source (for LLM fallback)
# Expected at: src/main/cpp/llama.cpp/
# ─────────────────────────────────────────────────────────────────────────────
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

if(EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    # Disable everything we don't need for an Android JNI library
    set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
    set(GGML_METAL           OFF CACHE BOOL "" FORCE)
    set(GGML_CUDA            OFF CACHE BOOL "" FORCE)
    set(GGML_VULKAN          ON  CACHE BOOL "" FORCE)  # GPU acceleration via Vulkan
    set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

    add_subdirectory(${LLAMA_CPP_DIR} llama_build)
    set(LLAMA_AVAILABLE TRUE)

    # llama.h lives in include/ on some versions, root on others
    if(EXISTS ${LLAMA_CPP_DIR}/include/llama.h)
        set(LLAMA_INCLUDE_DIR ${LLAMA_CPP_DIR}/include)
    else()
        set(LLAMA_INCLUDE_DIR ${LLAMA_CPP_DIR})
    endif()
    message(STATUS "llama.cpp found — building with GGUF LLM support")
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
    target_include_directories(whisperlm_jni PRIVATE ${WHISPER_INCLUDE_DIR})
    target_link_libraries(whisperlm_jni whisper)
    target_compile_definitions(whisperlm_jni PRIVATE WHISPER_AVAILABLE=1)
endif()

if(LLAMA_AVAILABLE)
    target_include_directories(whisperlm_jni PRIVATE ${LLAMA_INCLUDE_DIR})
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
