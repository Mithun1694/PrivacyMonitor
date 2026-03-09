# Privacy Shield 

Privacy Shield is a proactive privacy monitoring application for Android. It tracks and logs when other applications on your device access sensitive hardware components like the **Camera**, **Microphone**, and **GPS (Location)**, providing you with full transparency over your device's privacy.

## Features 

- **Live Status Dashboard**: Real-time indicators showing if your Camera, Microphone, or GPS is currently in use.
- **Detailed Usage History**: A persistent log of hardware access events, including:
    - The name and icon of the app responsible.
    - The specific hardware used.
    - Action type (Started/Stopped).
    - Precise timestamps.
- **Package Attribution Engine**: A dual-detection system using Accessibility Services and Usage Stats to accurately identify background hardware users.
- **Export to CSV**: Export your privacy logs to a CSV file for external analysis or backup.
- **Filter & Search**: Easily filter history logs by hardware type (Camera, Mic, GPS).
- **Auto-Start**: Automatically resumes monitoring after your device restarts.
- **Battery Optimization Bypass**: Built-in guidance to ensure the service stays active in the background.
- **Permission Gateway**: A streamlined setup process to ensure all necessary privacy protections are enabled.

## Screenshots 

![WhatsApp Image 2026-03-09 at 5 05 10 PM](https://github.com/user-attachments/assets/4f0abdac-f565-48dc-9361-dfe85fb67b3f)

![WhatsApp Image 2026-03-09 at 5 05 10 PM (1)](https://github.com/user-attachments/assets/c7c08fbb-7f01-4fd7-99f2-93fb39905903)

![WhatsApp Image 2026-03-09 at 5 05 11 PM](https://github.com/user-attachments/assets/b4fcb942-f2cd-4069-88f6-85859d4fe54e)


## How It Works 

1. **Monitoring**: The app uses system-level callbacks (`CameraManager`, `AudioManager`, `GnssStatus`) to detect hardware engagement.
2. **Identification**: When hardware usage is detected, Privacy Shield queries the `AccessibilityService` and `UsageStatsManager` to identify the foreground or most recently active application.
3. **Logging**: All events are stored locally in a secure **Room Database**.

## Installation & Setup 

1. Clone the repository.
2. Build and install the APK using Android Studio.
3. Upon first launch, grant the following required permissions:
    - **Camera**: For monitoring camera availability.
    - **Location**: For tracking GPS hardware usage.
    - **Notifications**: To maintain the foreground monitoring service.
    - **Accessibility Service**: To identify active applications.
    - **Usage Access**: As a fallback for background app identification.
    - **Battery Optimization**: Exemption to allow consistent background monitoring.

## Technical Stack 

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room Persistence Library
- **Background Processing**: Foreground Services
- **Architecture**: MVVM / Flow

## Compatibility 

- **Minimum SDK**: Android 13 (API 33)
- **Target SDK**: Android 15 (API 35)


