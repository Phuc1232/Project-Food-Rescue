# FoodRescue Android

Open android/ in Android Studio. The Gradle application module is :app.
Source package and namespace: com.foodrescue.app.
Application ID remains com.example.foodrescue.

## Structure
- android/app/src/main/java/com/foodrescue/app/app: navigation and session scaffolding.
- core: shared infrastructure; designsystem/theme contains the existing Compose theme.
- feature: feature packages, ready for implementation.
- dashboard: customer, owner, staff and admin packages.
- di: placeholders for future dependency providers.
- android/app/src/test and androidTest: unit and instrumented tests.

Navigation currently displays the original greeting. Authentication, role enforcement,
network, database and dependency injection providers are not implemented yet.

## Validation
From android/:
    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest

Instrumented tests require an emulator or device.
Existing root .idea and .gradle directories are local IDE/cache data from the old layout;
open android/ as the project rather than the workspace root.
