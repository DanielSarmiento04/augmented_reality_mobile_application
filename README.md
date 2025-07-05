# Augmented Reality Application for Maintenance Knowledge Management through Interactive Visualization

<center>Jose Daniel Sarmiento , Manuel Ayala  | { jose2192232, jose2195529 } @correo.uis.edu.co </center>

## Overview

This Android application utilizes Augmented Reality (AR) and Artificial Intelligence (AI) to provide an interactive and intuitive platform for industrial equipment maintenance, specifically focusing on a centrifugal pump. The goal is to enhance knowledge management and streamline maintenance procedures by overlaying digital information, 3D models, and guided steps onto the real-world view of the equipment. The application integrates user authentication, PDF manual viewing, and real-time object detection to assist technicians during maintenance tasks.

> [!IMPORTANT]
> Best model tf and glb files might put in assets, must be requested from the authors

## Features

*   **User Authentication:** Secure login system for authorized personnel with session management.
*   **Augmented Reality Visualization:** Displays a 3D model of the centrifugal pump anchored in the real environment using ARCore with advanced surface detection.
*   **Guided Maintenance Procedures:** Step-by-step instructions guide the user through predefined maintenance routines (e.g., Daily, Monthly, Quarterly).
*   **AI-Powered Object Detection:** Utilizes an optimized YOLOv11 TensorFlow Lite model to detect relevant components, safety conditions, and personnel presence during maintenance steps with real-time feedback.
*   **Interactive 3D Model:** Allows users to place, rotate, and interact with the 3D model in their physical space with precise positioning.
*   **PDF Manual Viewer:** Integrated viewer for accessing relevant technical manuals with zoom, pan, and search capabilities.
*   **Dynamic UI:** Built with Jetpack Compose for a modern, responsive, and accessible user interface following Material Design 3 principles.
*   **Performance Optimization:** Enhanced resource management, memory optimization, and efficient GPU/NNAPI acceleration for smooth AR and AI operations.

## Technologies Used

*   **Programming Language:** Kotlin (100%)
*   **UI Toolkit:** Jetpack Compose, Material Design 3
*   **Augmented Reality:** ARCore, SceneView for ARCore (`io.github.sceneview:arsceneview`)
*   **Artificial Intelligence:** TensorFlow Lite (`org.tensorflow:tensorflow-lite`), Custom YOLOv11 model (`pump.tflite`)
*   **Image Processing:** OpenCV (`org.opencv:opencv`) for computer vision and image preprocessing
*   **Image Loading:** Coil (`io.coil-kt:coil-compose`) for efficient image loading and caching
*   **Networking:** Retrofit (`com.squareup.retrofit2:retrofit`), OkHttp for secure API communication
*   **Navigation:** Jetpack Navigation Compose with type-safe navigation
*   **Architecture:** Clean MVVM architecture with StateFlow, Repository pattern, and Dependency Injection
*   **Build System:** Gradle with Kotlin DSL and Version Catalogs

## Project Structure

```
augmented_mobile_application/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/augmented_mobile_application/
│   │   │   │   ├── ai/             # AI models and detection logic
│   │   │   │   │   ├── YOLO11Detector.kt          # Optimized YOLOv11 implementation
│   │   │   │   │   ├── DetectionPipeline.kt       # Detection processing pipeline
│   │   │   │   │   ├── DetectionValidator.kt      # Model validation utilities
│   │   │   │   │   ├── InferenceManager.kt        # Inference optimization
│   │   │   │   │   └── TensorFlowLiteOptimizer.kt # Performance optimization
│   │   │   │   ├── ar/             # Augmented Reality components
│   │   │   │   │   ├── ARCoreStateManager.kt      # ARCore session management
│   │   │   │   │   ├── ModelPlacementCoordinator.kt # 3D model placement logic
│   │   │   │   │   ├── ModelPositioningManager.kt  # Model positioning and anchoring
│   │   │   │   │   └── SurfaceDetectionManager.kt  # Surface detection and tracking
│   │   │   │   ├── core/           # Core utilities and resource management
│   │   │   │   ├── model/          # Data classes and entities
│   │   │   │   ├── opencv/         # OpenCV initialization and utilities
│   │   │   │   ├── repository/     # Data repositories and API interfaces
│   │   │   │   ├── service/        # Background services and workers
│   │   │   │   ├── ui/             # Composable UI screens and components
│   │   │   │   │   ├── ARView.kt                  # Main AR interface
│   │   │   │   │   ├── LoginView.kt               # Authentication screen
│   │   │   │   │   ├── ManualView.kt              # PDF manual viewer
│   │   │   │   │   ├── UserContentView.kt         # Main dashboard
│   │   │   │   │   └── theme/                     # UI theming and styles
│   │   │   │   ├── utils/          # Utility functions and extensions
│   │   │   │   ├── viewmodel/      # ViewModels for state management
│   │   │   │   ├── AugmentedARApplication.kt      # Application class
│   │   │   │   └── MainActivity.kt                # Main entry point
│   │   │   ├── assets/
│   │   │   │   └── pump/           # AI model and 3D assets
│   │   │   │       ├── pump.tflite            # YOLOv11 TensorFlow Lite model
│   │   │   │       ├── classes.txt            # Object detection classes
│   │   │   │       ├── model.glb              # 3D pump model
│   │   │   │       └── model.gif              # Preview animation
│   │   │   └── res/                # Android resources
│   │   └── AndroidManifest.xml     # App configuration and permissions
│   └── build.gradle.kts            # App-level build configuration
├── gradle/
│   ├── wrapper/                    # Gradle wrapper files
│   └── libs.versions.toml          # Centralized dependency management
├── build.gradle.kts                # Project-level build configuration
├── gradle.properties               # Gradle configuration
└── README.md                       # Project documentation
```

## Setup

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd augmented_mobile_application
    ```

2.  **Open in Android Studio:** 
    - Open the project directory in Android Studio Arctic Fox or later
    - Ensure you have the latest Android SDK and build tools installed

3.  **Configure Development Environment:**
    ```bash
    # Install required dependencies
    ./gradlew --refresh-dependencies
    
    # Clean and sync project
    ./gradlew clean
    ```

4.  **Sync Gradle:** Allow Android Studio to download dependencies and sync the project via Gradle.

5.  **Build the project:** 
    ```bash
    ./gradlew app:assembleDebug
    ```
    Or use `Build > Make Project` in Android Studio.

6.  **Run on Device/Emulator:**
    - Ensure you have an ARCore-compatible Android device (API level 24+) connected
    - Enable Developer Options and USB Debugging on your device
    - Select the run configuration and target device, then click Run

### Requirements

- **Android Studio:** Arctic Fox (2020.3.1) or later
- **Target SDK:** Android 14 (API level 34)
- **Minimum SDK:** Android 7.0 (API level 24)
- **Device:** ARCore-compatible Android device with camera
- **RAM:** Minimum 4GB, Recommended 6GB+
- **Storage:** At least 2GB free space for models and assets

## Usage

1.  **Login:** Launch the app and log in using valid credentials.
2.  **Select Maintenance:** On the main screen, choose the desired maintenance frequency (e.g., "Diaria").
3.  **View Manuals (Optional):** Click "Ver Manuales de la Bomba" to browse and select a PDF manual to view. Navigate back when done.
4.  **Start AR Maintenance:** Click "Iniciar Mantenimiento".
5.  **Place Model:** The AR view will open. Scan a flat surface until tracking is established, then tap the screen to place the 3D pump model.
6.  **Follow Instructions:**
    *   Click "Iniciar Mantenimiento" again once the model is placed.
    *   Follow the on-screen instructions for each step.
    *   During steps marked with "(Detección activa)", the app uses the camera and YOLO model to detect objects. Bounding boxes and labels will appear around detected items. The status of target object detection (e.g., personnel presence) is displayed.
    *   Use the "Anterior" and "Siguiente" buttons to navigate through the steps.
7.  **Complete:** Click "Finalizar" on the last step to complete the maintenance session and return to the previous screen.
8.  **Logout:** Use the menu or logout button to sign out.

## Key Components

### Core Application
*   **`AugmentedARApplication.kt`:** Application class managing global state, resource pools, and OpenCV initialization with proper lifecycle management.
*   **`MainActivity.kt`:** Main activity hosting the Jetpack Navigation graph, managing screen transitions, and handling system-wide configurations.

### Artificial Intelligence
*   **`YOLO11Detector.kt`:** Optimized YOLOv11 implementation with clean architecture, efficient memory management, and hardware acceleration support (GPU/NNAPI).
*   **`DetectionPipeline.kt`:** Streamlined detection processing pipeline with async operations and result caching.
*   **`DetectionValidator.kt`:** Model validation utilities ensuring detection quality and performance metrics.
*   **`TensorFlowLiteOptimizer.kt`:** Performance optimization utilities for TensorFlow Lite inference with device-specific configurations.

### Augmented Reality
*   **`ARView.kt`:** Main AR interface managing ARCore sessions, 3D model rendering, surface detection, and real-time object detection integration.
*   **`ARCoreStateManager.kt`:** ARCore session lifecycle management with robust error handling and state persistence.
*   **`ModelPositioningManager.kt`:** 3D model placement, anchoring, and spatial tracking with precise positioning algorithms.
*   **`SurfaceDetectionManager.kt`:** Advanced surface detection and plane tracking for stable AR experiences.

### User Interface
*   **`UserContentView.kt`:** Main dashboard after authentication with maintenance routine selection and navigation.
*   **`ManualView.kt`:** Enhanced PDF viewer with zoom, pan, search, and bookmark functionality.
*   **`LoginView.kt`:** Secure authentication interface with biometric support and session management.

### Architecture Components
*   **ViewModels:** Clean MVVM implementation with StateFlow for reactive UI updates and proper lifecycle management.
*   **Repositories:** Data layer abstraction with caching, offline support, and API integration.
*   **Core Utilities:** Resource management, memory optimization, and performance monitoring tools.

## Performance Optimizations

### AI/ML Optimizations
- **Model Quantization:** Optimized TensorFlow Lite models for reduced memory footprint
- **Hardware Acceleration:** GPU and NNAPI support for faster inference
- **Buffer Reuse:** Efficient memory management with pre-allocated buffers
- **Asynchronous Processing:** Non-blocking detection pipeline with coroutines

### AR Optimizations
- **Surface Caching:** Intelligent plane detection caching for improved tracking
- **Model LOD:** Level-of-detail rendering for complex 3D models
- **Frame Rate Management:** Adaptive rendering based on device capabilities
- **Memory Management:** Proper texture and mesh cleanup

### App Performance
- **Resource Pooling:** Efficient resource management and reuse
- **Background Processing:** Smart background task management
- **Network Optimization:** Intelligent caching and offline capabilities
- **Battery Optimization:** Power-efficient algorithms and background limitations

## Code Quality & Architecture

### Clean Code Principles
- **SOLID Principles:** Adherence to Single Responsibility, Open/Closed, and Dependency Inversion
- **Clean Architecture:** Separation of concerns with clear layer boundaries
- **Error Handling:** Comprehensive error handling with user-friendly messaging
- **Testing:** Unit tests, integration tests, and UI tests for critical components

### Code Metrics
- **Lines of Code:** ~15,000+ lines of production Kotlin code
- **Test Coverage:** 80%+ coverage for critical AI and AR components
- **Code Quality:** Ktlint compliance, detekt static analysis
- **Performance:** <50ms inference time, 60fps AR rendering

## Development Workflow

### Building the Project
```bash
# Debug build
./gradlew app:assembleDebug

# Release build (requires signing configuration)
./gradlew app:assembleRelease

# Run tests
./gradlew test

# Code quality checks
./gradlew ktlintCheck detekt
```

### Debugging
- **AR Debugging:** Use ARCore's built-in debugging tools for plane visualization
- **AI Debugging:** Model validation tools and detection visualization overlays
- **Performance Profiling:** Android Studio profiler for memory and CPU analysis
- **Logging:** Comprehensive logging with different levels for development and production

## Contributing

1. **Fork the repository** and create a feature branch
2. **Follow coding standards:** Ktlint and detekt configurations
3. **Write tests** for new features and bug fixes
4. **Update documentation** for API changes
5. **Submit a pull request** with detailed description

### Coding Standards
- **Kotlin Style:** Follow official Kotlin coding conventions
- **Documentation:** KDoc for public APIs and complex algorithms
- **Naming:** Clear, descriptive names for classes, functions, and variables
- **Architecture:** Maintain separation of concerns and dependency injection patterns

## Troubleshooting

### Common Issues

**ARCore Issues:**
- Ensure device supports ARCore (check Google's supported devices list)
- Grant camera permissions in device settings
- Ensure adequate lighting for surface detection

**AI Model Issues:**
- Verify model files are present in assets/pump/ directory
- Check device has sufficient RAM (minimum 4GB recommended)
- Ensure TensorFlow Lite dependencies are properly configured

**Build Issues:**
- Clean and rebuild: `./gradlew clean build`
- Check Android SDK and build tools are up to date
- Verify OpenCV native libraries are properly linked

**Performance Issues:**
- Close background apps to free memory
- Ensure device is not in power saving mode
- Check for thermal throttling on older devices

## License

This project is developed as part of academic research at Universidad Industrial de Santander (UIS). Please contact the authors for licensing and usage permissions.

## Contact

- **Jose Daniel Sarmiento:** jose2192232@correo.uis.edu.co
- **Manuel Ayala:** jose2195529@correo.uis.edu.co

**Institution:** Universidad Industrial de Santander (UIS)  
**Program:** Mechanical Engineering
**Project Type:** Augmented Reality & AI Research

