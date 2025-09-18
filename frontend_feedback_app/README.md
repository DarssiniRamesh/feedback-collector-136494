# Frontend Feedback App (Android)

A minimalist Android app that lets users submit their name and feedback, persists entries locally with SharedPreferences, and shows all feedback in reverse chronological order (latest first). The UI follows the Ocean Professional theme: blue and amber accents, rounded corners, subtle shadows, and a subtle gradient background.

## Features
- Feedback form with Name and Feedback fields
- Persist data using SharedPreferences (data survives app restarts)
- List of submissions appears below the form, newest first
- Clean, modern styling with rounded surfaces and gradients

## Build and Run
- Build: `./gradlew build`
- Install debug on a connected device: `./gradlew :app:installDebug`
- Launch the app named "Frontend Feedback App"

No additional dependencies are required beyond AndroidX baseline and the existing Gradle Declarative setup.