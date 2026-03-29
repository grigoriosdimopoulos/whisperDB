package com.whisperlm.app.ml.llm

/**
 * JNI wrapper class whose native methods are implemented in llm_jni.cpp.
 * LlmEngine delegates to these when the GGUF backend is active.
 * Separated to keep the JNI symbol names clean.
 */
class LlamaEngine {
    external fun nativeLoadModel(modelPath: String, nCtx: Int): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int): String
    external fun nativeFreeModel()
    external fun nativeIsLoaded(): Boolean
}
