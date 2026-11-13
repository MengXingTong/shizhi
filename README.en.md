# Shizhi · 拾知

**Turn videos that interest you into knowledge you can listen to.**

[简体中文](README.md) · English

Shizhi is a personal listening app that runs independently on an Android phone. It discovers Bilibili videos based on your interests, reads available subtitles, uses your configured AI service to select and summarize content, and reads the summaries aloud with the phone's text-to-speech (TTS) engine. No self-hosted desktop backend is required; discovery and summarization still need an internet connection.

> **This is the Android validation build `0.1.3-alpha1`, not a production release.** The end-to-end workflow on a real phone with a real Bilibili account and model service remains unverified. Automated tests do not guarantee real-device compatibility.

## Features

- **Discover content by interest:** describe topics you want or wish to exclude, or summarize a specific BVID or video URL.
- **Keep a listening queue ready:** production stops when the queue is full and replenishes it as you listen, without requiring a fresh start every few items.
- **Listen and check the source:** summaries include subtitle quotations and timestamps, with access to the original video. Replaying a saved summary does not call the model again.
- **Keep useful material:** history, favorites, and sentence-level playback progress. Regular history is retained for 7 days; favorites are exempt.
- **Choose your AI service:** configure an HTTPS endpoint, API key, and model name for a Chat Completions-compatible service.
- **Manage data on your phone:** local settings and history, no self-hosted server, no cloud sync, and no original-video downloads.

## Getting started

### 1. Requirements and installation

You need Android 8.0 (API 26) or later, a working Android WebView, a Chinese TTS voice, a Bilibili account you are authorized to use, and a compatible AI service.

The current build output is `dist/shizhi-0.1.3-alpha1-debug.apk`. The `dist/` directory is not version-controlled: if you have the source code, build it using the instructions below rather than treating this path as a download link. Only install APKs from sources you trust.

To update, install over the existing app with the same package name and signing certificate. **Do not uninstall first** if you want to retain settings, history, and favorites. Keep the local debug keystore when rebuilding: a differently signed APK cannot directly replace the installed app. Debug signing is not intended for production distribution.

### 2. Configure the model and voice

1. Open **Settings** (设置) in the bottom navigation. Save the **HTTPS Base URL, API key, and exact model name**.
   - Example for OpenAI's official API: `https://api.openai.com/v1`. Use your provider's required base path; the app does not automatically append `/v1`.
   - Leaving the API-key field blank preserves the saved key. Explicitly select the clear option to remove it.
   - If your model does not support `max_tokens`, select `max_completion_tokens` instead.
2. Under **Discover → Feature settings** (刷视频 → 功能设置), save your interests and exclusions, then sign in through Bilibili's official web page. If it does not return automatically, use **Check again** (重新检查).
3. Select **Test voice** (测试语音). If no Chinese voice is available, install or enable one in the device's speech settings first.

**Test connection** (测试连接) sends a real model request after confirmation and may incur a charge. Never include API keys or cookies in issue reports or chats.

### 3. Try one video before continuous listening

1. In **Verify one video first** (先验证一个视频), enter a BVID or full Bilibili video URL that you can access and know has subtitles.
2. Select **Check subtitles** (检查字幕). This does not call the model. Missing subtitles do not trigger video downloads or automatic speech recognition.
3. Once subtitles are available, choose **Summarize this video** (提炼这条), confirm the data destination and potential cost, and check the summary, quotations, and speech output.
4. When the single-video workflow succeeds, return to **To listen** (待听) and select **Discover videos** (刷视频) to continuously select and queue content.

## Queue behavior and cost control

| Action or state | Behavior |
| --- | --- |
| Queue reaches its target | The default is 3 pending items, configurable from 1 to 10, excluding the current playback item. Production stops when full, with no further model calls. |
| Pause / resume discovery | Keeps the queue. These controls are separate from audio play / pause. |
| Start a fresh discovery session | After confirmation, moves old pending items to history and applies your latest preferences. Current playback and favorites are preserved. Saving preferences alone does not clear the queue. |
| No matching content | Continues at a reduced frequency without lowering your relevance threshold or imposing a fixed batch-count limit. |
| Request fails | Temporary Bilibili read failures can retry automatically. Model requests are not blindly replayed. Actionable errors retain the queue and offer a recovery entry. |
| App closes or is terminated | Retains the queue and progress. On restart, offers a way to continue rather than automatically issuing paid requests again. |

Discovering new candidates can incur selection and summarization costs. **Pause discovery**, not just audio, to stop new requests. A provider may still bill a request it already started processing, even if canceled. Whether TTS uses the network depends on the installed speech engine.

## Current limitations

- Bilibili recommendations and subtitles rely on unofficial interfaces. API changes, account access, login state, or risk controls may prevent access. The app does not bypass CAPTCHAs, payment requirements, or access restrictions.
- Only the first part of a multipart video is read. If subtitles exceed the configured input limit (12,000 characters by default), only the beginning is summarized and is labeled as an excerpt.
- Source binding, quotations, and timestamps help check data flow and summaries. They are not independent certification of subtitle ownership or fact-checking of the video's claims.
- Android background limits, power management, notification permissions, and TTS differences affect operation. Lock-screen controls, background service behavior, and upgrade recovery still need real-device verification.
- The current implementation uses native Java capabilities with a bundled WebView interface. **It is not a completed Kotlin / Compose / Room implementation.** This English README does not mean the app has an English UI.

## Privacy and data flow

- API keys and Bilibili cookies are encrypted with AES-GCM using an Android Keystore-protected key. System app backup is disabled.
- Interests, candidate-video metadata, and subtitles are sent to your configured AI service. The app asks you to confirm the destination before first production and after changing the endpoint. That provider's policies govern its processing and retention.
- Cookies are used only for Bilibili requests, not sent to subtitle CDNs or the AI service. The official sign-in page is isolated from the local application interface.
- The local UI uses bundled resources rather than external application scripts. History is stored in local SQLite and does not contain raw credentials.
- Requests use HTTPS. Model POST requests are not automatically retried or replayed through a proxy, to avoid duplicate charges. This does not guarantee bypassing a system VPN.

## Build from source

**Requirements: Windows, PowerShell 7, and JDK 17.** The bootstrap script downloads and verifies Android API 35 and Build Tools 35.0.0.

Run from the project root:

```powershell
./scripts/bootstrap.ps1
./scripts/build.ps1 -JavaHome 'C:\Program Files\Java\jdk-17'
```

Gradle, Android Studio, and npm are not required to build the APK. Output goes to `dist/`; the debug signing key is in the ignored `tools/shizhi-debug.jks`. Do not commit signing keys, credentials, or machine-local configuration.

### Tests

```powershell
# JVM core tests; org.json is a test dependency and is not bundled in the APK.
./scripts/bootstrap-tests.ps1
./scripts/test-core.ps1 -JavaHome 'C:\Program Files\Java\jdk-17'

# Validate SQLite SQL using Python's standard library, not Android instrumentation.
python ./tests/test_sqlite.py

# Node.js / Playwright UI tests, using a locally installed Chrome.
npm install --ignore-scripts
npm run test:syntax
npm run test:ui
```

Set `CHROME_PATH` to select Chrome and `SHIZHI_NODE_MODULES` to an existing `node_modules` directory containing Playwright. Tests use fixtures and do not verify real Bilibili sign-in or paid model calls. See the [validation record](design/VALIDATION.md) for historical results and outstanding checks.

## Structure and documentation

```text
app/src/main/java/    Android services, networking, storage, and TTS
app/src/main/assets/  Bundled HTML / CSS / JavaScript interface
scripts/             SDK setup, build, and core-test scripts
tests/               JVM, SQLite, and browser tests
design/              Design, implementation decisions, and validation records
```

- [Changelog](CHANGELOG.md): release history and past fixes, separated from the user guide.
- [Product and architecture](design/ARCHITECTURE.md): includes planned architecture that is not yet implemented.
- [Implementation decisions](design/IMPLEMENTATION.md): current modules, technology choices, and unverified areas.
- [Validation record](design/VALIDATION.md): test results and the real-device checklist.
- [Interaction prototype](design/preview.html): an earlier design with sample data, not real results from the current app.

The detailed design and validation documents are currently in Chinese.

## License and third-party components

The project currently has no top-level open-source license. Publicly readable source does not by itself grant unrestricted rights to use, modify, or redistribute it. The bundled Lucide icons `0.468.0` have their own [license](app/src/main/assets/lucide-LICENSE.txt); it does not automatically apply to the rest of this project.
