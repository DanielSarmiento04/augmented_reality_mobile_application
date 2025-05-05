# Augmented Reality Application for Maintenance Knowledge Management through Interactive Visualization

<center>Jose Daniel Sarmiento , Manuel Ayala  | { jose2192232, jose2195529 } @correo.uis.edu.co </center>

## Overview

This Android application utilizes Augmented Reality (AR) and Artificial Intelligence (AI) to provide an interactive and intuitive platform for industrial equipment maintenance, specifically focusing on a centrifugal pump. The goal is to enhance knowledge management and streamline maintenance procedures by overlaying digital information, 3D models, and guided steps onto the real-world view of the equipment. The application integrates user authentication, PDF manual viewing, and real-time object detection to assist technicians during maintenance tasks.

## Features

*   **User Authentication:** Secure login system for authorized personnel.
*   **Augmented Reality Visualization:** Displays a 3D model of the centrifugal pump anchored in the real environment using ARCore.
*   **Guided Maintenance Procedures:** Step-by-step instructions guide the user through predefined maintenance routines (e.g., Daily, Monthly).
*   **AI-Powered Object Detection:** Utilizes a YOLOv11 TensorFlow Lite model to detect relevant components or safety conditions (like personnel presence) during specific maintenance steps, providing real-time feedback.
*   **Interactive 3D Model:** Allows users to place and potentially interact with the 3D model in their physical space.
*   **PDF Manual Viewer:** Integrated viewer for accessing relevant technical manuals with zoom and pan capabilities.
*   **Dynamic UI:** Built with Jetpack Compose for a modern and responsive user interface.

## Technologies Used

*   **Programming Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose, Material Design 3
*   **Augmented Reality:** ARCore, SceneView for ARCore ( `io.github.sceneview:arsceneview`)
*   **Artificial Intelligence:** TensorFlow Lite (`org.tensorflow:tensorflow-lite`), Custom YOLOv11 model (`pump.tflite`)
*   **Image Processing:** OpenCV (`org.opencv:opencv`) (Primarily for initialization, potential future use)
*   **Image Loading:** Coil (`io.coil-kt:coil-compose`) (For loading GIFs and potentially other images)
*   **Networking:** Retrofit (`com.squareup.retrofit2:retrofit`), OkHttp (For API communication, e.g., login)
*   **Navigation:** Jetpack Navigation Compose
*   **Architecture:** MVVM (ViewModel, StateFlow)
*   **Build System:** Gradle

## Project Structure

```
augmented_mobile_application/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/augmented_mobile_application/
│   │   │   │   ├── ai/             # AI models and detection logic (YOLO11Detector)
│   │   │   │   ├── model/          # Data classes (User, AuthState, etc.)
│   │   │   │   ├── opencv/         # OpenCV initialization helpers
│   │   │   │   ├── ui/             # Composable UI screens (LoginView, ARView, ManualView, etc.)
│   │   │   │   ├── viewmodel/      # ViewModels for managing UI state
│   │   │   │   └── MainActivity.kt # Main entry point, navigation setup
│   │   │   ├── assets/
│   │   │   │   └── pump/           # Assets for the pump (model.glb, model.gif, model.tflite, classes.txt)
│   │   │   └── res/                # Android resources (drawables, layouts, values)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts            # App-level build script
├── gradle/                         # Gradle wrapper files
│   └── libs.versions.toml          # Dependency management
├── build.gradle.kts                # Project-level build script
└── README.md                       # This file
```

## Setup

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    ```
2.  **Open in Android Studio:** Open the cloned project directory in Android Studio (latest stable version recommended).
3.  **Sync Gradle:** Allow Android Studio to download dependencies and sync the project via Gradle.
4.  **Build the project:** Use `Build > Make Project` or run the app configuration.
5.  **Run on Device/Emulator:**
    *   Ensure you have an ARCore-compatible Android device connected or an emulator configured.
    *   Select the run configuration and target device, then click Run.

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

*   **`MainActivity.kt`:** The main activity hosting the Jetpack Navigation graph, managing transitions between different screens (Login, User Content, Manuals, AR). Initializes OpenCV.
*   **`ARView.kt`:** The core AR screen. Manages the ARCore session, loads and places the 3D model (`pump.glb`), displays maintenance instructions, integrates with `YOLO11Detector` to process camera frames for object detection, and overlays detection results (bounding boxes, labels).
*   **`ManualView.kt`:** Displays PDF documents fetched based on the selected manual. Implements zoom, pan, and page navigation functionalities.
*   **`YOLO11Detector.kt`:** Handles loading the TensorFlow Lite YOLO model (`pump.tflite` and `classes.txt`), pre-processing camera frames (converting `Image` to `Bitmap`), running inference, and post-processing the results to generate `Detection` objects (bounding boxes, class IDs, confidence scores).
*   **`UserContentView.kt`:** The screen shown after login, allowing selection of maintenance routines and navigation to the Manual or AR views. Displays a GIF of the pump.
*   **ViewModels (`UserViewModel`, `ManualViewModel`):** Manage UI state and business logic related to user authentication and manual display, following the MVVM pattern.

