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
- **Photo & Video Capture**: Built-in camera support using CameraX for capturing high-quality photos and videos.
- **Real-time Preview**: Live camera preview with intuitive controls and status messaging.
- **Capture Details**: View and manage details of recently captured media.

### 🖼️ Media Library
- **Smart Organization**: Automatic media organization with advanced search and filtering.
- **Advanced Filtering**: Filter media by type (All, Photos, Videos) and custom tags.
- **Sorting Options**: Multiple sorting options including Date (Newest/Oldest), Name, and Size.
- **View Modes**: Toggle between Grid and List views for better browsing experience.
- **Combine Images**: Select multiple images to combine them into a single grid-style composition.
- **Fullscreen Viewer**: Immersive media viewing experience with zoom, rotation support, and title/subtitle overlays.

### 🎨 Media Editing
- **Image Editor**: Comprehensive photo editing tools.
- **Adjustments**: Fine-tune brightness and other parameters using intuitive sliders.
- **Transformations**: Rotate and crop images with real-time preview and custom crop overlays.
- **Metadata Management**: Edit titles and descriptions for both photos and videos.

### 🎬 Reel Studio
- **Video Staging**: Select multiple video clips to stage a new reel project.
- **Project Management**: Clear or stage selections to a dedicated reels directory.
- **Merge Preparation**: Prepared for seamless video merging using Media3.

### 📁 Album Management
- **Custom Albums**: Create and manage custom photo/video albums with custom cover images.
- **Album Detail**: View all media belonging to a specific album in a dedicated grid view.
- **Quick Add**: Easily add any media item to existing albums directly from the detail view.

### 📥 Import Functionality
- **Media Import**: Import photos and videos from device storage into the app's internal library.
- **Import with Metadata**: Set title, description, and even create a new album for items during the import process.
- **Real-time Progress**: Visual progress indicators and error handling during batch imports.

### 🎯 User Experience
- **Material Design 3**: Modern, intuitive interface following Google's latest design guidelines.
- **Neon Dark Theme**: Beautiful deep dark theme (OLED optimized) with neon accents (Violet, Cyan, Magenta).
- **Splash Screen**: Elegant splash screen with app branding.
- **Bottom Navigation**: Fast and easy navigation between Library, Camera, and Albums.
- **Smooth Transitions**: Fluid navigation using the Jetpack Navigation Component.

## 🛠️ Technical Stack

### Core Technologies
- **Kotlin**: Primary programming language (Kotlin 2.0).
- **Android SDK**: Targeting API 35 (Vanilla Ice Cream), minimum API 24 (Nougat).
- **Material Design 3**: UI/UX framework with custom neon styling.
- **ViewBinding**: Type-safe view binding for all layouts.
- **KSP**: Kotlin Symbol Processing for efficient Room database generation.

### Architecture
- **MVVM**: Model-View-ViewModel architecture pattern for clean separation of concerns.
- **Single Activity**: Modern architecture using a single activity and multiple fragments.
- **Navigation Component**: Centralized navigation handling with a declarative graph.
- **Room Database**: Local data persistence for media metadata and album relationships.
- **Manual DI**: Dependency Injection via `AppContainer` for simplified testing and management.

### Key Libraries
- **CameraX**: High-level camera API for capture and preview.
- **Media3 (ExoPlayer)**: Modern media playback and editing framework.
- **Coil**: Fast and lightweight image loading and caching (with video frame decoder support).
- **PhotoView**: Pinch-to-zoom image view for fullscreen media browsing.
- **Coroutines & Flow**: Reactive asynchronous programming and state management.
- **RecyclerView & ViewPager2**: Efficient handling of lists, grids, and swipable media pages.

## 📋 Requirements

- **Android Studio**: Ladybug or later (Kotlin 2.0 / AGP 8.x requires a recent version)
- **Android SDK**: API level 24 (Android 7.0) or higher
- **Java**: JDK 17 or later
- **Kotlin**: 2.0 or later

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone <repository-url>
cd Hiraeth-Flame
```

### 2. Open in Android Studio
1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the cloned `Hiraeth-Flame` directory
4. Wait for Gradle sync to complete

### 3. Build and Run
1. Connect an Android device or start an emulator
2. Select the device/emulator from the dropdown
3. Click the "Run" button (▶️) or press `Shift + F10`

### 4. Grant Permissions
The app requires the following permissions for full functionality:
- **Camera**: For photo/video capture.
- **Microphone**: For video recording.
- **Media Access**: For importing and managing existing media.

## 📱 App Structure

```
app/src/main/java/com/hiraeth/flame/
├── MainActivity.kt              # Main navigation host
├── SplashActivity.kt            # App entry & branding
├── HiraethApplication.kt        # Application class & DI container
├── data/                       # Data layer
│   ├── db/                     # Room entities, DAOs, and database
│   ├── local/                  # File-based media storage
│   └── repository/             # Media and Album repositories
├── di/                         # Dependency injection (AppContainer)
├── domain/                     # Business logic & use cases
└── ui/                         # UI layer
    ├── library/                # Media library grid/list & search
    ├── camera/                 # Camera capture interface
    ├── albums/                 # Album list & detail views
    ├── detail/                 # Media viewer & metadata editor
    ├── editor/                 # Image editing (crop, rotate, filters)
    ├── reel/                   # Reel Studio project staging
    ├── mediaimport/            # Media import workflow
    └── util/                   # UI utilities, adapters, and custom views
```

## 🎨 UI & Layouts

### 📚 Media Library (`fragment_library.xml`)
- **Search Bar**: Integrated `TextInputLayout` for real-time media searching.
- **Filter Bar**: Tonal buttons for quick filtering by media type.
- **Sort Spinner**: Dropdown for organizing media by various criteria.
- **Responsive Grid**: Dynamic `RecyclerView` supporting both list and grid orientations.

### 📸 Camera (`fragment_camera.xml`)
- **Live Preview**: Large `PreviewView` area for framing shots.
- **Dynamic Controls**: Streamlined buttons for switching between photo and video modes.
- **Status HUD**: Transparent overlay for real-time camera feedback.

### 🖼️ Media Detail (`fragment_media_detail.xml`)
- **Media Pager**: `ViewPager2` for swiping through media items.
- **Metadata Editor**: Form-based interface for updating titles and descriptions.
- **Action Suite**: Quick access to Image Editor, Album management, and deletion.

### 🎨 Image Editor (`fragment_image_editor.xml`)
- **Canvas Area**: Large preview area with interactive crop overlays.
- **Adjustment Sliders**: Material Design sliders for precision editing.
- **Tool Palette**: Icons for rotation and cropping operations.

### 📁 Album Management (`fragment_albums.xml`, `fragment_album_detail.xml`)
- **Album Grid**: Visual cards for albums with cover previews.
- **Detail View**: Dedicated grid showing content filtered by the selected album.
- **Creation Dialog**: Prompt for naming and creating new collections.

### 🎞️ Reel Studio (`fragment_reel.xml`)
- **Clip Selector**: Selection-based list for ordering video clips.
- **Staging Dashboard**: Controls for project management and export preparation.

### 📥 Media Import (`fragment_import.xml`)
- **Batch Preview**: Visual confirmation of items selected for import.
- **Metadata Intake**: Pre-fill information before items enter the library.
- **Album Assignment**: Option to auto-categorize imported items.

## 🔧 Configuration

### Build Configuration
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 24
- **Java Version**: 17
- **Kotlin Target JVM**: 17

## 📄 License

This project is proprietary software. All rights reserved.

## 👤 Author

**Christopher Lee Cajes**

---

## 🤝 Contributing

This is a personal project. For contributions or issues, please contact the author directly.

---

*Built with ❤️ using modern Android development practices*
