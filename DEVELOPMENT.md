# PromptForge Development Guide

## Quick Start

### Prerequisites

1. **Android Studio Hedgehog (2023.1.1)** or newer
2. **JDK 17** 
3. **Android SDK 34**
4. **Device/Emulator** with Android 10+ (API 29+)

### Setup Steps

```bash
# 1. Clone the repository
git clone https://github.com/adwaizer/promptforge.git
cd promptforge

# 2. Download the Gemma 2B model from HuggingFace
pip install huggingface_hub
python -c "
from huggingface_hub import hf_hub_download
hf_hub_download(
    repo_id='t-ghosh/gemma-tflite',
    filename='gemma-1.1-2b-it-cpu-int4.bin',
    local_dir='app/src/main/assets/models'
)
"

# 3. Verify model is in place (~1.3GB)
ls -lh app/src/main/assets/models/gemma-1.1-2b-it-cpu-int4.bin

# 4. Open in Android Studio and sync Gradle

# 5. Build and run on device
./gradlew installDebug
```

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│                         UI Layer                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │
│  │ MainActivity│ │ShareReceiver│ │FloatingWidget│ │  Keyboard │ │
│  └──────┬──────┘ └──────┬──────┘ └──────┬───────┘ └─────┬─────┘ │
└─────────┼───────────────┼───────────────┼───────────────┼───────┘
          │               │               │               │
          └───────────────┴───────┬───────┴───────────────┘
                                  ▼
┌────────────────────────────────────────────────────────────────┐
│                      Service Layer                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   PromptForgeService                      │  │
│  │  - Foreground service keeping model warm                  │  │
│  │  - Provides enhancement API to all UI components          │  │
│  │  - Manages notifications and clipboard                    │  │
│  └──────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────┘
                              ▼
┌────────────────────────────────────────────────────────────────┐
│                      Core Layer                                 │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │  EnhancementEngine  │  │        GemmaInference           │  │
│  │  - Prompt templates │  │  - MediaPipe LLM wrapper        │  │
│  │  - Target adapters  │  │  - Model loading/inference      │  │
│  │  - Post-processing  │  │  - GPU/NPU acceleration         │  │
│  └──────────┬──────────┘  └─────────────┬───────────────────┘  │
└─────────────┼───────────────────────────┼───────────────────────┘
              │                           │
              └────────────┬──────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────┐
│                      Data Layer                                 │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │  PreferencesManager │  │       PromptRepository          │  │
│  │  (DataStore)        │  │       (Room Database)           │  │
│  └─────────────────────┘  └─────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. GemmaInference

The heart of on-device inference. Uses MediaPipe's LLM API with Gemma 1.1 2B IT INT4:

```kotlin
// Initialize
val options = LlmInferenceOptions.builder()
    .setModelPath("gemma-1.1-2b-it-cpu-int4.bin")
    .setMaxTokens(8192)
    .build()

val llmInference = LlmInference.createFromOptions(context, options)

// Generate
val response = llmInference.generateResponse(prompt)
```

**Performance Targets:**
- Cold start: < 5 seconds
- Warm inference: < 1s for typical prompts
- Memory: ~1.5GB when loaded

### 2. EnhancementEngine

Applies prompt engineering best practices:

```kotlin
// The enhancement prompt structure
val systemPrompt = """
You are PromptForge, an expert prompt engineer...
ENHANCEMENT LEVEL: ${level.name}
TARGET AI SYSTEM: ${targetAI.displayName}
${targetAI.systemHints}
"""
```

**Key Principles Applied:**
1. Specificity - Replace vague terms with concrete constraints
2. Structure - Add organization
3. Context - Include relevant background
4. Constraints - Define what output should/shouldn't include
5. Format - Specify output format
6. Examples - Add when helpful

### 3. UI Entry Points

| Entry Point | Trigger | Use Case |
|-------------|---------|----------|
| Share Menu | Select text → Share → PromptForge | Most common |
| Floating Widget | Tap bubble → Enhance clipboard | Quick access |
| Keyboard | Type → Tap "Enhance" key | In-place editing |
| Main App | Open app → Paste/Type → Enhance | Full options |

## Model Details

### Gemma 1.1 2B IT (INT4)

- **Architecture:** Decoder-only Transformer
- **Parameters:** 2 billion
- **Quantization:** INT4 (4-bit integers)
- **Size:** ~1.3GB on disk, ~1.5GB in memory
- **Context:** 8K tokens
- **Source:** HuggingFace (t-ghosh/gemma-tflite)

### Why Gemma 2B for This Use Case?

1. **Instruction-tuned for quality responses**
   - IT (Instruction Tuned) variant follows prompts well
   - Good understanding of prompt engineering concepts
   - High-quality text transformation

2. **2B provides excellent quality/performance balance**
   - Smart enough for nuanced prompt enhancement
   - Runs on devices with 6GB+ RAM
   - Sub-second inference on modern phones

3. **INT4 quantization**
   - 4x smaller than FP16
   - Minimal quality loss for text tasks
   - Enables CPU-optimized inference on mobile

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Manual Testing Checklist

- [ ] Cold start enhancement < 5s
- [ ] Warm enhancement < 1s
- [ ] Share menu works from Chrome, Twitter, Notes
- [ ] Floating widget drags, expands, enhances
- [ ] Keyboard enhance button replaces text
- [ ] History saves and displays
- [ ] Templates load and apply
- [ ] Settings persist across app restart
- [ ] Service survives app kill
- [ ] Notifications appear and copy works

## Performance Optimization

### Model Loading
- Load on service start (foreground service)
- Keep loaded while app is in use
- Release after 5 minutes of inactivity
- Reload on next use

### Inference Speed
- Use GPU delegate when available
- Fall back to NNAPI/NPU
- Use INT4 quantization
- Limit output tokens

### Battery
- Don't keep model loaded indefinitely
- Use efficient polling for accessibility
- Minimize wake locks

## Release Checklist

1. [ ] Update version in build.gradle
2. [ ] Run full test suite
3. [ ] Test on minimum SDK device (API 29)
4. [ ] Test on flagship device with NPU
5. [ ] Check ProGuard rules
6. [ ] Generate signed APK/AAB
7. [ ] Test signed build
8. [ ] Update changelog
9. [ ] Upload to Play Store

## Common Issues

### Model fails to load
- Check model file exists in assets/models/gemma-1.1-2b-it-cpu-int4.bin
- Verify model file is ~1.3GB (not corrupted)
- Ensure device has 6GB+ RAM

### Slow inference
- Enable GPU/NPU delegation
- Reduce max tokens
- Use smaller input prompts

### Service killed
- Check battery optimization settings
- Ensure foreground notification is showing
- Test on device with aggressive battery management

### Keyboard not appearing
- Enable in Settings → Language & Input → Manage Keyboards
- Grant necessary permissions
- Restart device if needed

## Contributing

1. Fork the repository
2. Create feature branch
3. Write tests
4. Submit PR with description

## License

MIT License - Adwaizer AI Consulting Inc.
