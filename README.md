# Live Translator (Android)

A starter Android app that listens to your voice, translates it live, and speaks the
translation back — fully on-device after the first-time language model download
(no API keys, no server, works offline once models are downloaded).

## How it works

1. **Speech → Text**: Android's built-in `SpeechRecognizer` (the same engine behind
   Google's mic input) converts what you say into text.
2. **Translate**: The text is passed to Google **ML Kit Translate**, which runs
   entirely on-device once the language pack is downloaded.
3. **Text → Speech**: The translated text is spoken aloud using Android's built-in
   `TextToSpeech` engine.
4. **"Live" behavior**: After each phrase, the recognizer automatically restarts, so
   you can keep talking and see/hear translations continuously until you tap the mic
   button again to stop.

## Languages included

English, Hindi, Bengali, Gujarati, Kannada, Marathi, Tamil, Telugu, Urdu, French,
German, Spanish, Arabic, Japanese, Korean, Portuguese, Russian, Chinese.

More can be added easily — see "Adding a language" below.

## Opening the project

1. Open **Android Studio** (Giraffe/2023.1 or newer recommended).
2. **File → Open** and select the `LiveTranslator` folder.
3. Android Studio will detect it's missing the Gradle wrapper and offer to generate
   one automatically — accept it (or run `gradle wrapper` from the command line if you
   have Gradle installed). Let it sync.
4. Connect a physical device or start an emulator (an emulator needs a virtual mic
   input configured, so a real device is easier for testing speech).
5. Click **Run ▶**.

## Permissions

On first launch, the app asks for microphone access. If you deny it, tap the mic
button again to be re-prompted.

## Notes & things to customize

- **First use of a language pair** downloads a small ML Kit model (a few MB) over
  whatever network is available; edit `DownloadConditions.Builder()` in
  `MainActivity.kt` (`setupTranslator()`) if you want to require Wi-Fi only.
- **Continuous listening** relies on the free on-device `SpeechRecognizer`. Some OEM
  Android builds restrict background/continuous recognition — if you hit issues,
  Google's official `SpeechRecognizer` app / Google app needs to be installed and
  set as the default assistant.
- **Adding a language**: add a new `Language(...)` entry to the `languages` list in
  `MainActivity.kt`. Use the matching constant from `TranslateLanguage` and a valid
  BCP-47 locale tag for speech/TTS (check ML Kit's supported language list for the
  full set — it currently supports 50+ languages).
- **TTS voice availability** depends on the voice packs installed on the device;
  if a target language's voice isn't installed, Android will prompt to download it
  from Settings → Language & Input → Text-to-speech.

## Project structure

```
LiveTranslator/
├── build.gradle, settings.gradle, gradle.properties
└── app/
    ├── build.gradle                 # dependencies (ML Kit Translate, AndroidX, Material)
    └── src/main/
        ├── AndroidManifest.xml      # RECORD_AUDIO permission, launcher activity
        ├── java/.../MainActivity.kt # all app logic
        └── res/
            ├── layout/activity_main.xml
            └── values/ (strings, colors, theme)
```
