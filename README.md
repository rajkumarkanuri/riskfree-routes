# RiskFree Routes 🛡️📍

An intelligent, safety-first Android navigation application that calculates and recommends the safest routes, not just the fastest ones. RiskFree Routes utilizes crowd-sourced incident reporting, historical crime data, lighting conditions, and live emergency response integration powered by **Firebase**.

---

## 🏗️ Architecture & Tech Stack

### Client (Android)
- **Language**: Java 8
- **UI Framework**: Modern Material Design 3 (Dark Obsidian theme) + ViewBinding
- **Architecture Pattern**: MVVM (Model-View-ViewModel with LiveData)
- **Maps & Location**:
  - Google Maps SDK
  - Google Places API (Autocomplete & Geocoding)
  - Google Directions API (Multi-alternative routing)
  - Android FusedLocationProviderClient (High-accuracy background GPS)

### Backend & Cloud Infrastructure (Firebase)
- **Firebase Authentication**: Email/Password and Google Sign-In with OAuth
- **Cloud Firestore**: Real-time NoSQL Database for user profiles, live navigation sessions, incident hazard reports, and trusted contacts
- **Firebase Hosting**: Serves the live web tracking interface (`public/track.html`) for emergency SMS recipients
- **Cloudinary SDK**: Secure cloud media storage for incident photo attachments

---

## 📁 Clean Project Structure

```
riskfree-routes/
├── firebase.json                 # Firebase Hosting and Firestore configuration
├── firestore.rules               # Firestore security rules
├── firestore.indexes.json        # Composite indexes for real-time spatial queries
├── public/
│   └── track.html                # Live GPS emergency web viewer for trusted contacts
│
└── android/
    ├── build.gradle              # Project-level Gradle build configuration
    └── app/
        ├── build.gradle          # App-level dependencies (Firebase, Maps, Material 3)
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/riskfreeroutes/app/
            │   ├── RiskFreeRoutesApp.java      # Application class entry point
            │   ├── maps/                       # Location tracking & turn-by-turn math
            │   │   ├── LocationHelper.java
            │   │   └── NavigationTracker.java
            │   ├── model/                      # Clean data models
            │   │   ├── User.java
            │   │   ├── Route.java
            │   │   ├── Journey.java
            │   │   ├── CommunityReport.java
            │   │   ├── CommunityReportItem.java
            │   │   ├── ReportCategory.java
            │   │   ├── SafePlace.java
            │   │   ├── TrustedContact.java
            │   │   ├── SosEvent.java
            │   │   ├── Notification.java
            │   │   └── Settings.java
            │   ├── repository/                 # Firestore & API Data Access Layer
            │   │   ├── UserRepository.java
            │   │   ├── DirectionsRepository.java
            │   │   ├── PlacesRepository.java
            │   │   ├── ReportRepository.java
            │   │   ├── FirestoreReportsRepository.java
            │   │   ├── GuardianRepository.java
            │   │   ├── JourneyHistoryRepository.java
            │   │   ├── LiveShareRepository.java
            │   │   ├── NearbyPlacesRepository.java
            │   │   ├── NotificationRepository.java
            │   │   ├── TrustedContactRepository.java
            │   │   ├── ActiveRouteRepository.java
            │   │   └── SettingsRepository.java
            │   ├── service/                    # Android Background Services
            │   │   ├── LocationTrackingService.java # Foreground GPS location tracker
            │   │   ├── VoiceTriggerService.java     # Hands-free "help me guardian" hotword detector
            │   │   └── SmsHelper.java               # Automated SMS dispatcher with live tracking links
            │   ├── ui/                         # Presentation Layer (MVVM)
            │   │   ├── auth/                   # Login & Register
            │   │   ├── home/                   # Main Map, Search autocomplete & BottomSheet
            │   │   ├── routes/                 # Multi-route comparison & safety score engine
            │   │   ├── navigation/             # Active turn-by-turn navigation & Safe Arrival
            │   │   ├── reports/                # Community hazard reporting & report list
            │   │   ├── contacts/               # Trusted contacts management
            │   │   ├── emergency/              # Emergency Countdown & SOS triggers
            │   │   ├── nearby/                 # Safe places filter (Police, Hospitals, Pharmacies)
            │   │   ├── journey/                # Journey history & stats
            │   │   ├── profile/                # User profile & saved locations
            │   │   ├── settings/               # Safety & navigation settings
            │   │   └── splash/                 # Splash screen
            │   └── utils/                      # Utilities
            │       ├── SafetyScorer.java       # Multi-factor safety scoring algorithm
            │       ├── SafetyScoreResult.java
            │       ├── ReportVerificationHelper.java
            │       ├── PolylineDecoder.java
            │       └── DistanceUtils.java
            └── res/                            # Material 3 dark layouts, themes, and drawables
```

---

## ⚡ Key Features

1. **Multi-Factor Safety Engine**: Evaluates candidate routes based on lighting conditions, incident proximity, time-of-day risk, and crowd verified hazards.
2. **Community Incident Reporting**: Allows users to post verified hazards with location pins, severity tags, and photo evidence.
3. **Emergency SOS & Hands-free Voice SOS**: Fast trigger countdown that dispatches automated SMS alerts with live GPS tracking links to trusted contacts.
4. **Nearby Safe Havens**: Instant search and routing to nearby 24/7 Police stations, hospitals, and pharmacies.
5. **Live Web Tracking**: Trusted contacts can follow the traveler in real-time in any web browser without needing the app installed.
