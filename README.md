# Hiraeth Flame

A modern Android multimedia application for capturing, editing, and managing photos and videos with a sleek dark theme interface.

> "A dragon breaths fire with no smoke."
> — Christopher Lee Cajes

## 🐉 The Purpose of Silent Success

In a world filled with noise and constant self-promotion, **Hiraeth Flame** embodies the philosophy of silent success. Just as a dragon breathes fire without the obscuring cloud of smoke, true excellence speaks through its results rather than its clamor. We believe in building with precision, performing with power, and succeeding with humility. The fire of innovation burns brightest when it is pure, direct, and undistracted by the superficial.

## 📱 Overview

Hiraeth Flame is a comprehensive media management app built with modern Android development practices. It features camera capture, media editing, library management, and album organization capabilities with a beautiful Material Design 3 dark theme.

## ✨ Features

### 📸 Camera & Capture
- **Photo & Video Capture**: Built-in camera support for capturing high-quality photos and videos
- **Camera2 API Integration**: Advanced camera controls and capabilities
- **Real-time Preview**: Live camera preview with intuitive controls

### 🖼️ Media Library
- **Smart Organization**: Automatic media organization with search and filtering capabilities
- **Tag Support**: Tag-based media categorization and filtering
- **Sort Options**: Multiple sorting options (date, name, size, etc.)
- **Import Functionality**: Import media from device storage

### 🎨 Media Editing
- **Image Editor**: Comprehensive photo editing tools with drawing overlay support
- **Video Editor**: Video trimming and editing capabilities
- **Drawing Overlay**: Draw and annotate directly on images
- **Real-time Preview**: See edits in real-time before saving

### 📁 Album Management
- **Custom Albums**: Create and manage custom photo/video albums
- **Smart Collections**: Automatic album creation based on criteria
- **Quick Access**: Fast navigation between different media collections

### 🎯 User Experience
- **Material Design 3**: Modern, intuitive interface following Google's design guidelines
- **Dark Theme**: Beautiful dark theme optimized for OLED displays
- **Splash Screen**: Elegant splash screen with app branding
- **Bottom Navigation**: Easy navigation between main sections
- **Status Bar Integration**: Proper system UI integration with no overlap

## 🛠️ Technical Stack

### Core Technologies
- **Kotlin**: Primary programming language (Kotlin 2.0)
- **Android SDK**: Targeting API 35, minimum API 24
- **Material Design 3**: UI/UX framework
- **ViewBinding**: Type-safe view binding
- **KSP**: Kotlin Symbol Processing for Room database

### Architecture
- **MVVM**: Model-View-ViewModel architecture pattern
- **Navigation Component**: Single-activity architecture with fragment navigation
- **Room Database**: Local data persistence with high-performance DAO
- **Dependency Injection**: Manual DI setup via AppContainer

### Key Libraries
- **CameraX**: Camera functionality and preview
- **ExoPlayer (Media3)**: Video playback and editing
- **Coil**: Image loading and caching
- **Coroutines**: Asynchronous programming
- **RecyclerView**: Efficient list/grid displays

## 📋 Requirements

- **Android Studio**: Arctic Fox or later
- **Android SDK**: API level 24 (Android 7.0) or higher
- **Java**: JDK 17 or later
- **Kotlin**: 1.9.0 or later

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone <repository-url>
cd HiraethFlame
```

### 2. Open in Android Studio
1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the cloned `HiraethFlame` directory
4. Wait for Gradle sync to complete

### 3. Build and Run
1. Connect an Android device or start an emulator
2. Select the device/emulator from the dropdown
3. Click the "Run" button (▶️) or press `Shift + F10`

### 4. Grant Permissions
The app requires the following permissions:
- **Camera**: For photo/video capture
- **Microphone**: For video recording
- **Storage**: For media access and management

## 📱 App Structure

```
app/src/main/java/com/hiraeth/flame/
├── MainActivity.kt              # Main activity with navigation
├── SplashActivity.kt            # Splash screen activity
├── data/                       # Data layer (models, repositories)
├── di/                         # Dependency injection
├── domain/                     # Business logic
└── ui/                         # UI layer (fragments, adapters, utilities)
    ├── editor/                 # Media editing components
    ├── library/                # Library management
    └── util/                   # Utility classes
```

## 🎨 UI Components

### Main Navigation
- **Library**: Browse and manage all media files
- **Camera**: Capture photos and videos
- **Albums**: Organize media into collections

### Key Screens
- **Splash Screen**: App introduction with branding
- **Media Library**: Grid/list view of all media with search and filters
- **Camera Preview**: Live camera view with capture controls
- **Image Editor**: Photo editing interface with drawing tools
- **Video Editor**: Video trimming and editing interface
- **Album View**: Custom album management

## 🔧 Configuration

### Build Configuration
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 24
- **Java Version**: 17
- **Kotlin Target JVM**: 17

### Dependencies
All dependencies are managed through Gradle version catalogs. Key dependencies include:
- AndroidX libraries
- Material Design components
- CameraX for camera functionality
- ExoPlayer for media playback
- Room for database operations

## 📄 License

This project is proprietary software. All rights reserved.

## 👤 Author

**Christopher Lee Cajes**

---

## 🤝 Contributing

This is a personal project. For contributions or issues, please contact the author directly.

---

*Built with ❤️ using modern Android development practices*
