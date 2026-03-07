# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# AutoGLM For Android

Android app for AI-powered phone automation using Kotlin.

## Build & Run
- Build: `./gradlew assembleDebug`
- APK: `app/build/outputs/apk/debug/AutoGLM-0.0.6-debug.apk`
- ADB (WSL): `/mnt/c/Users/liuji/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Git
- Remote: `liujingtech` (not `origin`)
- Branch: `master` (not `main`)
- Push: `git push liujingtech master`

## Architecture

### Core Pattern
- MVVM with StateFlow for reactive UI
- Material Design 3 components
- SharedPreferences + EncryptedSharedPreferences for storage

### Execution Flow (PhoneAgent)
1. `PhoneAgent.run()` starts task loop
2. Each iteration: `ScreenshotService.capture()` → `ModelClient.request()` → `ActionHandler.execute()`
3. Loop continues until `AgentAction.Finish` or max steps reached
4. `ComponentManager` manages all singleton dependencies

### Key Components
- `ComponentManager`: Central DI container, initializes on Shizuku UserService connection
- `PhoneAgent`: Main agent loop (screenshot → model → action)
- `ActionHandler`: Dispatches actions to DeviceExecutor/TextInputManager
- `ModelClient`: SSE streaming API client
- `SettingsManager`: Encrypted config storage (API keys, model config)

### Package Structure
- `agent/`: PhoneAgent, AgentContext
- `action/`: AgentAction sealed class, ActionParser, ActionHandler
- `device/`: DeviceExecutor (Shizuku operations)
- `model/`: ModelClient, ModelConfig, network layer
- `screenshot/`: ScreenshotService
- `settings/`: SettingsManager, SettingsFragment
- `debug/`: Debug feature (templates, mock data, test history)

## Code Conventions
- Use `Logger.d(TAG, "message")` instead of `Log.d()` directly
- Each class should have `companion object { private const val TAG = "ClassName" }`
- Singleton pattern: `getInstance(context)` with `@Volatile` + `synchronized`

## Gotchas
- Settings test connection uses UI input values; `getModelConfig()` reads saved values
- EncryptedSharedPreferences may fail and fallback to regular prefs, losing encrypted data
- Color `text_tertiary` doesn't exist; use `text_hint` or `text_secondary`
