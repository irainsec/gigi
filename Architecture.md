# 🏗️ Detailed Application Architecture: RemindMe (Gigi)

This document provides a comprehensive breakdown of the **RemindMe** application's structure, focusing on its dual functionality: a robust task management system (Reminders) and a premium, highly customizable Caller Theme & Dialer engine.

---

## 🏗️ Architecture Overview
The application follows a modern **MVVM (Model-View-ViewModel)** architectural pattern with a strong emphasis on modularity and stability.

- **Dependency Injection**: [Hilt](file:///app/src/main/java/com/aman/gigi/hilt/AppModule.kt)
- **Database Persistence**: [Room](file:///app/src/main/java/com/aman/gigi/db/ReminderDatabase.kt)
- **UI Framework**: Jetpack Compose (Modern, declarative UI)
- **Persistence Layer**: Repository Pattern for data abstraction.

---

## 📁 Core Directory Structure

### 🛠️ 1. Root & Configuration
| File/Directory | Description |
| :--- | :--- |
| `app/src/main/AndroidManifest.xml` | Declares all activities, services (InCall, Alarm), and required permissions (CALL_PHONE, CALL_LOG, etc.). |
| `app/proguard-rules.pro` | Rules for code shrinking and obfuscation. |

### 🧠 2. Application Logic (`com.aman.gigi`)
- [**RemindMe.kt**](file:///app/src/main/java/com/aman/gigi/RemindMe.kt): The base `Application` class. Initializes Hilt and global app-wide configurations.

### ⏰ 3. Alarm Engine (`com.aman.gigi.alarm`)
Manages the lifecycle of task-based notifications.
- [**AlarmUtils.kt**](file:///app/src/main/java/com/aman/gigi/alarm/AlarmUtils.kt): Helper for scheduling, snoozing, and canceling system alarms.
- [**AlarmReceiver.kt**](file:///app/src/main/java/com/aman/gigi/alarm/AlarmReceiver.kt): BroadcastReceiver that catches scheduled alarms and triggers notifications or activities.
- [**BootReceiver.kt**](file:///app/src/main/java/com/aman/gigi/alarm/BootReceiver.kt): Ensures alarms are rescheduled after a device reboot.

### 💾 4. Database & Persistence (`com.aman.gigi.db`)
Handles all local data storage for both reminders and caller themes.
- [**ReminderDatabase.kt**](file:///app/src/main/java/com/aman/gigi/db/ReminderDatabase.kt): Main Room database instance.
- [**ReminderDAO.kt**](file:///app/src/main/java/com/aman/gigi/db/ReminderDAO.kt) / [**CallerThemeDao.kt**](file:///app/src/main/java/com/aman/gigi/db/CallerThemeDao.kt): Data access objects defining SQL queries.
- [**Repositories**](file:///app/src/main/java/com/aman/gigi/db/ReminderRepository.kt): Clean API for the rest of the app to interact with the database.
- [**Converters**](file:///app/src/main/java/com/aman/gigi/model/RecurrencePatternConverter.kt): Handles complex data types (enums, custom lists) for Room.

### 📞 5. Telephony Services (`com.aman.gigi.service`)
The "Gigi" engine that manages incoming/outgoing calls and the system dialer.
- [**GigiInCallService.kt**](file:///app/src/main/java/com/aman/gigi/service/GigiInCallService.kt): Extends `InCallService` to provide the custom call-handling interface.
- [**CallReceiver.kt**](file:///app/src/main/java/com/aman/gigi/service/CallReceiver.kt): Detects phone state changes.
- [**AlarmForegroundService.kt**](file:///app/src/main/java/com/aman/gigi/service/AlarmForegroundService.kt): Keeps the app active during critical alarm states.

### 🍱 6. Model Layer (`com.aman.gigi.model`)
Pure data structures.
- [**Reminder.kt**](file:///app/src/main/java/com/aman/gigi/model/Reminder.kt): Definition of a task (Title, Time, Recurrence, Priority).
- [**RecurrencePattern.kt**](file:///app/src/main/java/com/aman/gigi/model/RecurrencePattern.kt): Logic for Daily, Weekly, and Monthly task repetition.

---

## 🎨 UI Layer Breakdown (`com.aman.gigi.ui`)

### 🏠 Main Navigation
- [**MainActivity.kt**](file:///app/src/main/java/com/aman/gigi/ui/MainActivity.kt): The shell activity hosting the Bottom Navigation (Reminders, Phone, Developer, Settings).
- [**GlassBottomNavigation.kt**](file:///app/src/main/java/com/aman/gigi/ui/components/GlassBottomNavigation.kt): Reusable glassmorphic navigation bar.

### 📑 Key Screens
- [**Reminders.kt**](file:///app/src/main/java/com/aman/gigi/ui/Reminders.kt): Main list and interactive cards for task management.
- [**Phone.kt**](file:///app/src/main/java/com/aman/gigi/ui/Phone.kt): The hub for Dialer, Call Logs, and Theme Builder.
- [**Developer.kt**](file:///app/src/main/java/com/aman/gigi/ui/Developer.kt): Personal profile showcase.

### 📞 Special Call UI Components (`com.aman.gigi.ui.call`)
- [**IncomingCallUI.kt**](file:///app/src/main/java/com/aman/gigi/ui/call/IncomingCallUI.kt): The core "vibe" renderer for incoming calls.
- [**WallpaperRenderer.kt**](file:///app/src/main/java/com/aman/gigi/ui/call/WallpaperRenderer.kt): High-performance component for Static/Video/Lottie backgrounds.
- [**DialerUI.kt**](file:///app/src/main/java/com/aman/gigi/ui/call/DialerUI.kt) / [**CallLogUI.kt**](file:///app/src/main/java/com/aman/gigi/ui/call/CallLogUI.kt): Functional dialpad and history views.
- [**ParticleOverlay.kt**](file:///app/src/main/java/com/aman/gigi/ui/call/ParticleOverlay.kt): Visual atmosphere effects (Rain, Glow, Particles).

---

## 📦 Resources (`app/src/main/res`)

- **Drawable**: SVG icons (ic_alarm, ic_calendar) and layout background assets.
- **Mipmap**: Adaptive app icons.
- **Layout**: Android XML (mostly legacy as the app uses Compose).

---

## 🔧 Utilities & Constants
- [**Utils.kt**](file:///app/src/main/java/com/aman/gigi/utils/Utils.kt): Shared helper logic for formatting times and generating colors.
- [**Constants.kt**](file:///app/src/main/java/com/aman/gigi/utils/Constants.kt): App-wide fixed values (Channel IDs, Key names).
