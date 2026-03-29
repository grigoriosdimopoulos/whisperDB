#include <jni.h>
#include <cmath>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AudioUtilsNative"

extern "C" {

/**
 * Simple triangular window resampler from any sample rate to 16000 Hz.
 * Input: raw PCM16 as byte array (little-endian), inputSampleRate
 * Output: float array at 16kHz
 */
JNIEXPORT jfloatArray JNICALL
Java_com_whisperlm_app_core_util_AudioUtils_nativeResampleTo16k(
        JNIEnv* env, jclass,
        jbyteArray inputPcm16, jint inputSampleRate) {
    jsize inputBytes = env->GetArrayLength(inputPcm16);
    jbyte* rawBytes = env->GetByteArrayElements(inputPcm16, nullptr);

    int inputSamples = inputBytes / 2;
    int outputSamples = (int)((long long) inputSamples * 16000 / inputSampleRate);

    std::vector<float> output(outputSamples);
    double ratio = (double) inputSampleRate / 16000.0;

    for (int i = 0; i < outputSamples; i++) {
        double srcIdx = i * ratio;
        int idx0 = (int) srcIdx;
        int idx1 = idx0 + 1;
        double frac = srcIdx - idx0;

        auto getSample = [&](int idx) -> float {
            if (idx < 0 || idx >= inputSamples) return 0.f;
            int byteIdx = idx * 2;
            int16_t sample = (int16_t)((uint8_t)rawBytes[byteIdx] | ((uint8_t)rawBytes[byteIdx + 1] << 8));
            return sample / 32768.0f;
        };

        output[i] = (float)((1.0 - frac) * getSample(idx0) + frac * getSample(idx1));
    }

    env->ReleaseByteArrayElements(inputPcm16, rawBytes, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(outputSamples);
    env->SetFloatArrayRegion(result, 0, outputSamples, output.data());
    return result;
}

} // extern "C"
