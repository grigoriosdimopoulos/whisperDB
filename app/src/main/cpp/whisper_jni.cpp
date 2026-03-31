#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef WHISPER_AVAILABLE
#include "whisper.h"

static struct whisper_context* g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeLoadModel(
        JNIEnv* env, jobject /* obj */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading whisper model: %s", path);

    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    whisper_context_params cparams = whisper_context_default_params();
    g_ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_ctx) {
        LOGE("Failed to load whisper model");
        return JNI_FALSE;
    }
    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeTranscribe(
        JNIEnv* env, jobject /* obj */,
        jfloatArray pcmData, jint sampleRate,
        jstring language, jboolean translate) {
    if (!g_ctx) {
        LOGE("Whisper model not loaded");
        return nullptr;
    }

    jfloat* pcm = env->GetFloatArrayElements(pcmData, nullptr);
    jsize pcmLen = env->GetArrayLength(pcmData);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    const char* lang = env->GetStringUTFChars(language, nullptr);
    params.language = lang[0] == 0 ? nullptr : lang;  // null = auto-detect
    params.translate = (bool) translate;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = true;
    params.token_timestamps = true;
    params.single_segment = false;
    params.no_context = true;
    params.n_threads = 4;

    int result = whisper_full(g_ctx, params, pcm, (int)pcmLen);
    env->ReleaseFloatArrayElements(pcmData, pcm, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);

    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return nullptr;
    }

    int numSegments = whisper_full_n_segments(g_ctx);
    LOGI("Transcription produced %d segments", numSegments);

    // Return as String array: each entry = "startMs|endMs|text"
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArr = env->NewObjectArray(numSegments, stringClass, nullptr);

    for (int i = 0; i < numSegments; i++) {
        int64_t startMs = whisper_full_get_segment_t0(g_ctx, i) * 10;  // centiseconds → ms
        int64_t endMs   = whisper_full_get_segment_t1(g_ctx, i) * 10;
        const char* text = whisper_full_get_segment_text(g_ctx, i);

        std::string entry = std::to_string(startMs) + "|" + std::to_string(endMs) + "|" + (text ? text : "");
        jstring jEntry = env->NewStringUTF(entry.c_str());
        env->SetObjectArrayElement(resultArr, i, jEntry);
        env->DeleteLocalRef(jEntry);
    }

    return resultArr;
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeFreeModel(
        JNIEnv* /* env */, jobject /* obj */) {
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        LOGI("Whisper model freed");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeIsLoaded(
        JNIEnv* /* env */, jobject /* obj */) {
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

#else

// ── Stub implementations when whisper.cpp is not present ──────────────────
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeLoadModel(
        JNIEnv*, jobject, jstring) {
    LOGE("whisper.cpp not compiled in - model load stub");
    return JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeTranscribe(
        JNIEnv*, jobject, jfloatArray, jint, jstring, jboolean) {
    LOGE("whisper.cpp not compiled in - transcribe stub");
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeFreeModel(
        JNIEnv*, jobject) {}

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_whisper_WhisperEngine_nativeIsLoaded(
        JNIEnv*, jobject) { return JNI_FALSE; }

} // extern "C"
#endif
