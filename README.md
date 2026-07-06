# HuiLian — ComfyUI Mobile Client

> An Android app built with Jetpack Compose and Material Design 3 that lets you connect to, manage, and run ComfyUI workflows from anywhere.

[中文](README_zh.md)

---

## Features

### Home · Server Management
- One-tap connect / disconnect to ComfyUI server
- Real-time latency detection with color-coded status: green (≤300ms), red (>300ms)
- Manual address input or one-tap LAN scan for ComfyUI instances
- Local server integration via Termux API

### Workflow · Edit & Run
- JSON workflow editor with real-time editing and node parameter adjustment
- Node-level configuration (model, sampler, steps, CFG, dimensions, etc.)
- One-tap workflow execution with real-time progress pushed to queue

### Queue · Task Management
- Real-time progress tracking via WebSocket push
- Task list: 35×35 thumbnail + image name + completion time + canvas size
- Green progress bar synced with task progress
- **Swipe-to-delete**: running tasks are interrupted, completed tasks also delete server images
- Tap thumbnail for fullscreen view with double-tap zoom, pinch zoom, and pan

### Gallery · Image Browser
- Grid layout for browsing generated images
- Fullscreen viewer with:
  - Horizontal swipe to switch between images
  - Double-tap zoom (1x ↔ 2.5x)
  - Pinch-to-zoom (1x ~ 5x)
  - Single-finger pan with edge bounce-back
- Save to device gallery and share

### Global Interaction
- **Edge swipe back gesture**: unified left/right edge swipe to navigate back across the entire app
- Fullscreen viewer intelligently disables global edge gesture to avoid conflicts
- Material Design 3 theme with system dark mode support

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material Design 3 |
| Networking | Retrofit (HTTP) + Ktor Client (WebSocket) |
| Image Loading | Coil |
| Architecture | MVVM (ViewModel + MutableState) |
| Serialization | Gson |
| Min SDK | Android 8.0 (API 26) |

---

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- A ComfyUI server (local or remote)

### Build

```bash
git clone https://github.com/erniugzs/comfyui-app.git
cd comfyui-app
./gradlew :app:assembleDebug
```

APK output: `app/apks/`

### Connect to Server

1. Open the app and expand the **Server** card
2. Enter your ComfyUI address (e.g. `http://192.168.1.100:8188`)
3. Tap **Connect**
4. Once the status turns green, you're ready to go

---

## Project Structure

```
app/src/main/java/com/huilian/comfymobile/
├── MainActivity.kt          # Entry point, global gestures, navigation
├── MainViewModel.kt         # Business logic, network requests, state management
├── BottomNavItem.kt         # Bottom navigation definitions
├── data/
│   ├── ComfyUIService.kt    # Retrofit API interface
│   ├── RetrofitClient.kt    # Network client configuration
│   ├── WebSocketManager.kt  # WebSocket connection & event parsing
│   └── models/              # Data models (QueueItem, SavedWorkflow, etc.)
├── screens/
│   ├── HomeScreen.kt        # Home (server, quick actions)
│   ├── WorkflowScreen.kt    # Workflow editor & runner
│   ├── WorkflowListScreen.kt # Workflow list
│   ├── GalleryScreen.kt     # Gallery browser
│   ├── QueueScreen.kt       # Queue management
│   └── SettingsScreen.kt    # Settings
└── components/
    └── SwipeableImageViewer.kt  # Fullscreen image viewer (zoom/pan/swipe)
```

---

## License

MIT License

---

## Acknowledgements

- [ComfyUI](https://github.com/comfyanonymous/ComfyUI) — Powerful Stable Diffusion workflow backend
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI toolkit
- [Coil](https://coil-kt.github.io/coil/) — Kotlin image loading library
