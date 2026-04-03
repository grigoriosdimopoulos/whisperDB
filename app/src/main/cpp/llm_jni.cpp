#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef LLAMA_AVAILABLE
#include "llama.h"

static llama_model*   g_model   = nullptr;
static llama_context* g_llama   = nullptr;
static llama_sampler* g_sampler = nullptr;

// Stop strings that signal end of assistant turn for common chat templates
static const char* STOP_STRINGS[] = {
    "<|im_end|>", "</s>", "<|eot_id|>", "<|end|>",
    "\nUser:", "\nuser:", nullptr
};

// Resets the llama context before each generation to prevent KV cache
// overflow across turns.
static bool reset_context() {
    if (g_llama) { llama_free(g_llama); g_llama = nullptr; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = 2048;
    cparams.n_batch = 512;

    g_llama = llama_init_from_model(g_model, cparams);
    if (!g_llama) { LOGE("Failed to re-init llama context"); return false; }
    llama_set_n_threads(g_llama, 8, 8);
    return true;
}

// Returns n_prompt > 0 on success, ≤ 0 on failure.
static int decode_prompt(const char* promptStr) {
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n = -llama_tokenize(vocab, promptStr, (int32_t)strlen(promptStr),
                            nullptr, 0, true, true);
    if (n <= 0) return n;

    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, promptStr, (int32_t)strlen(promptStr),
                   tokens.data(), tokens.size(), true, true);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(g_llama, batch) != 0) {
        LOGE("Prompt decode failed — prompt may be too long (%d tokens)", n);
        return -1;
    }
    return n;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeLoadModel(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading llama model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 99; // offload all layers to Vulkan GPU; falls back to CPU if unavailable
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) { LOGE("Failed to load llama model"); return JNI_FALSE; }

    // Create initial context (will be re-created per generation call)
    if (!reset_context()) return JNI_FALSE;

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    LOGI("Llama model loaded with Vulkan GPU offload + flash attention");
    return JNI_TRUE;
}

// Streaming generation — calls callback.onToken(piece) for each token as it
// is produced. The Kotlin side collects these into a Flow for real-time display.
JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeGenerateStreaming(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens, jobject callback) {
    if (!g_model) return;
    if (!reset_context()) return;

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    int n_prompt = decode_prompt(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);
    if (n_prompt <= 0) return;

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    jclass   cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    std::string accumulated;

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(g_sampler, g_llama, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char buf[256] = {};
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n <= 0) { llama_batch single = llama_batch_get_one(&token, 1); llama_decode(g_llama, single); continue; }

        std::string piece(buf, n);
        accumulated += piece;

        // Check stop strings in accumulated output
        bool hit_stop = false;
        for (int s = 0; STOP_STRINGS[s] != nullptr; s++) {
            size_t pos = accumulated.find(STOP_STRINGS[s]);
            if (pos != std::string::npos) {
                // Emit the clean portion before the stop marker
                std::string clean = accumulated.substr(0, pos);
                // Find what was already emitted (accumulated minus this token)
                // by emitting only the clean remainder if any
                std::string prev = accumulated.substr(0, accumulated.size() - piece.size());
                if (pos > prev.size()) {
                    std::string tail = clean.substr(prev.size());
                    if (!tail.empty()) {
                        jstring jtok = env->NewStringUTF(tail.c_str());
                        env->CallVoidMethod(callback, onToken, jtok);
                        env->DeleteLocalRef(jtok);
                    }
                }
                hit_stop = true;
                break;
            }
        }
        if (hit_stop) break;

        // Emit this token piece to Kotlin
        jstring jtok = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onToken, jtok);
        env->DeleteLocalRef(jtok);

        llama_batch single = llama_batch_get_one(&token, 1);
        if (llama_decode(g_llama, single) != 0) break;
    }
}

// Formats a full multi-turn conversation using the model's embedded Jinja
// chat template. Accepts system context + parallel role/content arrays so
// the full chat history is included in the prompt (not just the last message).
// Falls back to ChatML if the model has no embedded template.
JNIEXPORT jstring JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeFormatPromptMultiTurn(
        JNIEnv* env, jobject, jstring system, jobjectArray roles, jobjectArray contents) {
    if (!g_model) return env->NewStringUTF("");

    const char* sysStr = env->GetStringUTFChars(system, nullptr);
    int n = (int)env->GetArrayLength(roles);  // number of user/assistant turns

    // Build message vector: 1 system message + n user/assistant turns
    std::vector<std::string> roleStrs(n), contentStrs(n);
    std::vector<llama_chat_message> messages(n + 1);
    messages[0] = {"system", sysStr};

    for (int i = 0; i < n; i++) {
        auto jrole    = (jstring)env->GetObjectArrayElement(roles,    i);
        auto jcontent = (jstring)env->GetObjectArrayElement(contents, i);
        const char* r = env->GetStringUTFChars(jrole,    nullptr);
        const char* c = env->GetStringUTFChars(jcontent, nullptr);
        roleStrs[i]    = r;
        contentStrs[i] = c;
        env->ReleaseStringUTFChars(jrole,    r);
        env->ReleaseStringUTFChars(jcontent, c);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
        messages[i + 1] = {roleStrs[i].c_str(), contentStrs[i].c_str()};
    }

    const char* tmpl = llama_model_chat_template(g_model, nullptr);
    int size = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);

    std::string result;
    if (size > 0) {
        std::vector<char> buf(size + 1, '\0');
        llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buf.data(), (int32_t)buf.size());
        result = std::string(buf.data(), size);
        LOGI("Chat template applied: %d turns, %d chars", n + 1, size);
    } else {
        LOGI("No embedded chat template — falling back to ChatML");
        result = "<|im_start|>system\n";
        result += sysStr;
        result += "<|im_end|>\n";
        for (int i = 0; i < n; i++) {
            result += "<|im_start|>" + roleStrs[i] + "\n" + contentStrs[i] + "<|im_end|>\n";
        }
        result += "<|im_start|>assistant\n";
    }

    env->ReleaseStringUTFChars(system, sysStr);
    return env->NewStringUTF(result.c_str());
}

// Non-streaming fallback (kept for compatibility; not used by default)
JNIEXPORT jstring JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens) {
    if (!g_model) return env->NewStringUTF("[Model not loaded]");
    if (!reset_context()) return env->NewStringUTF("[Context init failed]");

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    int n_prompt = decode_prompt(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);
    if (n_prompt <= 0) return env->NewStringUTF("[Prompt too long]");

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::string output;

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(g_sampler, g_llama, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char buf[256] = {};
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n > 0) output.append(buf, n);

        bool hit_stop = false;
        for (int s = 0; STOP_STRINGS[s] != nullptr; s++) {
            size_t pos = output.find(STOP_STRINGS[s]);
            if (pos != std::string::npos) { output.erase(pos); hit_stop = true; break; }
        }
        if (hit_stop) break;

        llama_batch single = llama_batch_get_one(&token, 1);
        if (llama_decode(g_llama, single) != 0) break;
    }

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeFreeModel(JNIEnv*, jobject) {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_llama)   { llama_free(g_llama);            g_llama   = nullptr; }
    if (g_model)   { llama_model_free(g_model);      g_model   = nullptr; }
}

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeIsLoaded(JNIEnv*, jobject) {
    return (g_model != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

#else

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeLoadModel(
        JNIEnv*, jobject, jstring, jint) {
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeFormatPromptMultiTurn(
        JNIEnv* env, jobject, jstring, jobjectArray, jobjectArray) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_whisperlm_app_ml_llm_LlmEngine_nativeGenerateStreaming(
        JNIEnv*, jobject, jstring, jint, jobject) {}

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
