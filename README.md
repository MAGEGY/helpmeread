# Help Me Read - Android OCR & Text-to-Speech App

An Android application designed to help people who cannot read text (illiterate, visually impaired, or foreign language speakers) by:
- **Capturing images** with the camera
- **Detecting text** using ML Kit OCR
- **Reading aloud** the detected text with adjustable volume and speed
- **Translating** text to 15+ supported languages

## Features

### Core Features
- **Camera with live preview** and a selection square
- **One-tap capture** - just point and tap the green button
- **Automatic text detection** with visual highlighting
- **Tap-to-read** - tap any highlighted text area to hear it
- **Read all** - reads all detected text sequentially with visual highlighting

### Language Support
| Language | Flag | Code |
|----------|------|------|
| English | 🇬🇧 | en |
| Arabic | 🇸🇦 | ar |
| French | 🇫🇷 | fr |
| Spanish | 🇪🇸 | es |
| German | 🇩🇪 | de |
| Hindi | 🇮🇳 | hi |
| Chinese | 🇨🇳 | zh |
| Japanese | 🇯🇵 | ja |
| Korean | 🇰🇷 | ko |
| Russian | 🇷🇺 | ru |
| Italian | 🇮🇹 | it |
| Portuguese | 🇧🇷 | pt |
| Turkish | 🇹🇷 | tr |
| Urdu | 🇵🇰 | ur |
| Bengali | 🇧🇩 | bn |

### Accessibility
- **No text instructions** - interface uses icons and colors
- **Audio feedback** - voice announces number of text areas found
- **High contrast** visual design
- **Large touch targets** for easy interaction

## Building the App

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Android device with API 24+ (Android 7.0+)

### Build Steps
1. Open the project in Android Studio
2. Sync Gradle files
3. Connect an Android device or start an emulator
4. Click "Run" or press Shift+F10

### Generate APK
```bash
./gradlew assembleRelease
```
APK will be in: `app/build/outputs/apk/release/`

## Project Structure

```
app/src/main/java/com/helpmeread/app/
├── data/
│   ├── Language.kt          # Language definitions
│   └── TextModels.kt        # Data classes for recognized text
├── ui/
│   ├── components/
│   │   ├── LanguageSelector.kt  # Flag-based language picker
│   │   └── VoiceSettings.kt     # Volume & speed controls
│   ├── screens/
│   │   ├── SplashScreen.kt      # App splash screen
│   │   ├── CameraScreen.kt      # Camera preview UI
│   │   └── ResultScreen.kt      # Text overlay UI
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── HelpMeReadApp.kt         # Main app composable
├── viewmodel/
│   └── AppViewModel.kt      # Business logic, TTS, OCR
├── HelpMeReadApp.kt         # Application class
└── MainActivity.kt          # Entry point
```

## How to Use

### First Launch
1. Open the app - it shows a splash screen
2. Grant camera permission when requested

### Capturing Text
1. Point the camera at text (sign, document, medicine label, map, etc.)
2. Use the green square in the center as a guide
3. Tap the large green capture button

### Reading Detected Text
1. After capture, text areas are highlighted with yellow rectangles
2. **Tap any rectangle** to hear that text read aloud
3. **Tap the play button** at the bottom to read all text sequentially
4. The currently reading text is highlighted in blue

### Changing Language
1. Tap the flag button (top-right in result screen, top-left in camera)
2. Select your preferred output language
3. Text will be translated before speaking

### Adjusting Voice
1. Tap the settings/gear or volume icon
2. Adjust volume slider (0-100%)
3. Adjust speech speed (Slow/Normal/Fast)
4. Tap "Test Voice" to preview settings

## Permissions Required
- **Camera** - for capturing images
- **Internet** - for ML Kit translation services
- **Storage** - for temporary image caching

## Technical Details

### Libraries Used
- **CameraX** - Camera preview and capture
- **ML Kit Text Recognition** - OCR for multiple scripts
- **ML Kit Translation** - On-device translation
- **Jetpack Compose** - Modern UI toolkit
- **Kotlin Coroutines** - Async operations
- **Text-to-Speech** - Android system TTS

### OCR Scripts Supported
- Latin (English, French, Spanish, etc.)
- Chinese (Simplified & Traditional)
- Devanagari (Hindi, Marathi, Sanskrit, Nepali)
- Japanese
- Korean

## Future Improvements
- [ ] Real-time text detection without capture
- [ ] Voice commands ("read", "stop", "next")
- [ ] Document edge detection and perspective correction
- [ ] Save favorite/important text
- [ ] Offline translation models
- [ ] Larger text preview for partially sighted users
- [ ] Vibration feedback for actions

## License
MIT License - Free for personal and commercial use.

## Contact
Created for helping people read - share freely with those in need.
