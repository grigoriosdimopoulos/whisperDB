#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef LLAMA_AVAILABLE
#include "llama.h"

static llama_model*   g_model   = nullptr;
static llama_context* g_llama   = nullptr;
static llama_sampler* g_sampler = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeLoadModel(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading llama model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) { LOGE("Failed to load llama model"); return JNI_FALSE; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx    = (uint32_t) nCtx;
    cparams.n_threads = 4;
    g_llama = llama_new_context_with_model(g_model, cparams);
    if (!g_llama) { LOGE("Failed to create llama context"); return JNI_FALSE; }

    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    LOGI("Llama model loaded");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens) {
    if (!g_llama || !g_model) return env->NewStringUTF("[Model not loaded]");

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string output;

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n_prompt = -llama_tokenize(vocab, promptStr, (int32_t)strlen(promptStr), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    llama_tokenize(vocab, promptStr, (int32_t)strlen(promptStr), tokens.data(), tokens.size(), true, true);
    env->ReleaseStringUTFChars(prompt, promptStr);

    llama_kv_cache_clear(g_llama);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    llama_decode(g_llama, batch);

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(g_sampler, g_llama, -1);
        if (llama_token_is_eog(vocab, token)) break;

        char buf[256] = {};
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n > 0) output.append(buf, n);

        llama_batch single = llama_batch_get_one(&token, 1);
        if (llama_decode(g_llama, single) != 0) break;
    }

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeFreeModel(JNIEnv*, jobject) {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_llama)   { llama_free(g_llama);   g_llama  = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }
}

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeIsLoaded(JNIEnv*, jobject) {
    return (g_llama != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

#else

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeLoadModel(
        JNIEnv*, jobject, jstring, jint) {
    LOGE("llama.cpp not compiled in");
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring, jint) {
    return env->NewStringUTF("[llama.cpp not compiled in]");
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeFreeModel(JNIEnv*, jobject) {}

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeIsLoaded(JNIEnv*, jobject) {
    return JNI_FALSE;
}

} // extern "C"
#endif
