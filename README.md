# PromptForge 🔥

**On-Device AI Prompt Optimizer for Android**

Transform any prompt into an optimized, high-quality instruction using Gemma 2B running locally on your device. Zero API costs, zero latency, complete privacy.

## Features

- 🚀 **System-Wide Enhancement**: Works across any app via Share Menu, Floating Widget, or Custom Keyboard
- 🔒 **100% On-Device**: Gemma 1.1 2B IT runs locally—no data leaves your phone
- ⚡ **Fast**: Sub-second inference with NPU/GPU acceleration
- 🎯 **Target-Aware**: Optimized outputs for Claude, GPT, Gemini, or generic AI
- 📚 **Template Library**: Pre-built enhancement patterns for common tasks
- 📊 **Analytics**: Track your prompt improvement over time

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      User Interfaces                         │
├─────────────────┬─────────────────┬─────────────────────────┤
│   Share Menu    │ Floating Widget │    Custom Keyboard      │
│   (Intent)      │ (Accessibility) │    (InputMethod)        │
└────────┬────────┴────────┬────────┴───────────┬─────────────┘
         │                 │                    │
         └─────────────────┼────────────────────┘
                           ▼
              ┌────────────────────────┐
              │   PromptForgeService   │
              │   (Foreground Service) │
              └───────────┬────────────┘
                          ▼
              ┌────────────────────────┐
              │   EnhancementEngine    │
              │   - Prompt Templates   │
              │   - Target Adapters    │
              │   - Quality Scoring    │
              └───────────┬────────────┘
                          ▼
              ┌────────────────────────┐
              │     GemmaInference     │
              │   (MediaPipe LLM API)  │
              │   - GPU/NPU Delegate   │
              │   - 2B INT4 Model      │
              └────────────────────────┘
```

## Requirements

- Android 10+ (API 29+)
- 6GB+ RAM recommended (model uses ~1.5GB in memory)
- ~1.3GB storage for model
- GPU or NPU for best performance

## Quick Start

1. Clone this repository
2. Download Gemma 2B model (see Setup)
3. Build and install
4. Grant necessary permissions
5. Start enhancing prompts!

## Model Setup

Download the quantized model from HuggingFace:
```bash
# Gemma 1.1 2B Instruction-Tuned, INT4 Quantized (~1.3GB)
pip install huggingface_hub
python -c "from huggingface_hub import hf_hub_download; hf_hub_download('t-ghosh/gemma-tflite', 'gemma-1.1-2b-it-cpu-int4.bin', local_dir='app/src/main/assets/models')"
```

Place in `app/src/main/assets/models/`

## Project Structure

```
app/
├── src/main/
│   ├── java/com/adwaizer/promptforge/
│   │   ├── PromptForgeApp.kt              # Application class
│   │   ├── MainActivity.kt                 # Main UI
│   │   ├── core/
│   │   │   ├── PromptForgeService.kt      # Background service
│   │   │   ├── EnhancementEngine.kt       # Core logic
│   │   │   └── GemmaInference.kt           # Model wrapper
│   │   ├── ui/
│   │   │   ├── FloatingWidgetService.kt   # Overlay widget
│   │   │   ├── EnhancementActivity.kt     # Quick enhance screen
│   │   │   └── SettingsActivity.kt        # Configuration
│   │   ├── keyboard/
│   │   │   ├── ForgeKeyboardService.kt    # IME implementation
│   │   │   └── ForgeKeyboardView.kt       # Keyboard layout
│   │   ├── share/
│   │   │   └── ShareReceiverActivity.kt   # Share intent handler
│   │   ├── data/
│   │   │   ├── PromptRepository.kt        # History & analytics
│   │   │   ├── TemplateRepository.kt      # Enhancement templates
│   │   │   └── PreferencesManager.kt      # User settings
│   │   └── model/
│   │       ├── EnhancementRequest.kt      # Data classes
│   │       ├── EnhancementResult.kt
│   │       ├── TargetAI.kt
│   │       └── Template.kt
│   ├── res/
│   └── assets/
│       └── models/                         # Gemma 2B model files
└── build.gradle.kts
```

## License

MIT License - Built by Adwaizer AI Consulting Inc.

## Author

Gabriel Ordoñez - Senior AI/ML Program Leader
- [Adwaizer AI Consulting](https://adwaizer.com)
