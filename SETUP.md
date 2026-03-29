# whisperLM — Model Setup Guide

whisperLM is fully offline. No internet connection is ever used. You must copy
AI model files manually to your device before the app can transcribe or chat.

## Required: Whisper Speech-to-Text Model

1. Download one of the following whisper.cpp compatible model files:
   - `whisper-tiny.bin` (~75 MB) — fastest, lower accuracy for Greek
   - `whisper-base.bin` (~142 MB) — good balance
   - `whisper-small.bin` (~466 MB) — **recommended** for Greek + English
   - `whisper-medium.bin` (~1.5 GB) — best accuracy, slower

   Get them from: https://huggingface.co/ggerganov/whisper.cpp
   (converted whisper.cpp `.bin` format)

2. Copy the file to your device storage (Downloads, USB, etc.)

3. On first launch, the app's setup wizard will prompt you to locate the file.

## Optional: LLM Model (for Chat feature)

The chat screen lets you ask questions about your stored conversations.
This requires one of:

### Option A — Gemma via MediaPipe (`.task` format)
- Download `gemma-2b-it-gpu-int4.task` (~1.6 GB)
- Source: Google AI Edge (requires sign-in on Google AI Studio)

### Option B — Any GGUF model (llama.cpp format)
- Download any GGUF Q4_K_M quantized model (~2-4 GB)
- Recommended: `Phi-3-mini-4k-instruct.Q4_K_M.gguf` (~2.2 GB)
- Source: HuggingFace (search for GGUF models)

Copy to device storage and select during setup (or via Settings > Add LLM Model).

## Optional: Speaker Diarization Model

For multi-speaker identification, place `diarization.onnx` in your models folder.
A compatible model is WeSpeaker ResNet34 exported to ONNX.

## Building from Source

### Prerequisites
- Android Studio Hedgehog or later
- NDK r25c or later (install via SDK Manager)
- CMake 3.22+

### Add whisper.cpp as submodule
```bash
cd app/src/main/cpp
git submodule add https://github.com/ggerganov/whisper.cpp
```

### Build
```bash
./gradlew assembleDebug
```

The NDK will compile whisper.cpp JNI bridge automatically via CMakeLists.txt.
If whisper.cpp source is absent, the build still succeeds with stub implementations
(STT will return empty results until the source is added).
