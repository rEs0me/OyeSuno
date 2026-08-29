# OyeSuno

OyeSuno is an all-in-one smart audio recording studio, sound library, and waveform editor built for Android.

Whether you are recording lectures, capturing podcast interviews, making quick voice memos, scheduling automated recordings, or editing audio snippets on the go, OyeSuno gives you a clean, responsive, and powerful studio right on your phone.

---

## Why OyeSuno?

Most voice recorder apps look like tools built ten years ago: basic lists, static buttons, and zero editing tools. OyeSuno reimagines audio on Android with a modern, design-first interface:

- Live Waveform Metering: Watch sound come to life in real time as you speak.
- Interactive Waveform Scrubbing: Navigate through your recordings visually by dragging across smooth animated waveforms.
- Built-In Audio Editor: Trim, cut, slice, and export clean audio clips without needing a computer.
- Hands-Free Wake-Word Detection: Start recording hands-free with voice activation.
- Scheduled Auto-Recordings: Set recurring time-based recording jobs that run reliably in the background.
- Smart Library & Bookmarks: Add timestamps, notes, and bookmarks during recordings to jump back to key moments instantly.
- 100% On-Device & Private: Your voice never leaves your device. No cloud sync, no tracking, zero internet required.

---

## Key Highlights

### 1. Studio-Quality Audio Capture
- Crisp recording formats: Uncompressed WAV for master quality, or compressed M4A, AAC, and MP3 for lightweight storage.
- Real-time animated amplitude visualization with smooth 60fps waveform meters.
- Background recording with lock screen controls and persistent notification status.
- Pause, resume, and add timestamped markers mid-recording.

### 2. Interactive Waveform Player
- Smooth fluid waveform visualization of recorded tracks.
- Variable playback speeds from 0.5x (slow transcription) to 2.5x (fast listening).
- Precise 5-second / 10-second skip intervals and loop playback.
- Jump directly between recorded bookmarks with a single tap.

### 3. Built-In Audio Editor
- Visual audio trimmer: Select start and end points with millimeter drag precision.
- Cut unwanted sections, remove silent gaps, and extract clips.
- Amplitude normalization and noise suppression for clean vocal clarity.
- Export edited versions as new audio files without overwriting the originals.

### 4. Smart Wake-Word Trigger
- Hands-free voice trigger activation.
- Configurable trigger keyword sensitivity and background listening modes.
- Low-power audio listening engine that preserves battery life.

### 5. Scheduled & Automated Recordings
- Schedule recordings ahead of time for meetings, classes, radio shows, or reminders.
- Set recurring rules (Daily, Weekdays, Weekly, or Custom dates).
- Powered by Android WorkManager to wake up and execute reliably even when the app is closed.

### 6. Library Management
- Organize recordings into custom folders (Work, Personal, Music, Interviews, Notes).
- Search across titles, tags, and recording dates.
- Star favorite tracks, bulk share, and manage storage usage with visual disk meters.

---

## Tech Highlights (For Developers)

- 100% Kotlin with modern Jetpack Compose UI (Material 3)
- Multi-Module Architecture: Decoupled into 15 independent modules separating audio capture, playback engines, database, storage, and UI screens
- Hardware Audio Engine: Built on low-latency Android `AudioRecord`, `MediaRecorder`, and `ExoPlayer`
- Background Task Pipeline: AndroidX `WorkManager` paired with foreground audio capture services
- Persistence: AndroidX Room SQLite with full-text search capabilities
- Asynchronous Concurrency: Kotlin Coroutines and StateFlow for unidirectional data streams
- Zero third-party ad SDKs or trackers

---

## Getting Started

### Requirements
- Android Studio Ladybug or newer
- JDK 17
- Android device running Android 8.0 (API 26) or higher

### Build and Install

Clone the repository and install the debug build on your connected device:

```bash
git clone https://github.com/tech-anupam/OyeSuno.git
cd OyeSuno
./gradlew installDebug
adb shell am start -n com.oyesuno.app/.MainActivity
```

---

## Author & Connect

Created and designed by Anupam Jha.

- GitHub: https://github.com/tech-anupam
- Portfolio: https://anupambuilds.store/about
- Instagram: https://instagram.com/tech.anupam (@tech.anupam)
- Email: triggeredgamerxdiscord@gmail.com

---

## License

This project is open-source and available under the [MIT License](LICENSE).
