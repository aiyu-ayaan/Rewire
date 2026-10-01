# Project: REWIRE

**Tagline:** Break habits. Build control.

## 1. Product Goal

Build Android app that helps users reduce bad habits by adding
intentional friction before distracting or habit-forming apps can be
used.

Core loop:

``` text
Trigger
  ↓
Protected app opened
  ↓
Habit detection
  ↓
Warning / restriction
  ↓
User decision
  ↓
Usage recorded
  ↓
Analytics
  ↓
User learns patterns
```

App must help users make deliberate choices, not blindly punish them.

------------------------------------------------------------------------

# 2. Brand Identity

REWIRE is behavior-control app focused on breaking unwanted habit loops through friction, awareness, deliberate choice, and measurable progress.

Brand:

```text
REWIRE

Break habits.
Build control.
```

Core product language:

```text
Friction → Awareness → Choice → Action → Measurement → Improvement
```

Avoid framing REWIRE as punishment, surveillance, or shame-based control.

# 3. Core Features

App has 4 bottom-navigation sections:

``` text
Guard
Focus
Matrix
Profile
```

## Guard

Controls protected apps and bad-habit rules.

Features:

- Add protected apps
- Group apps into habits
- Configure warning level
- Configure time boundaries
- Configure daily limits
- Configure launch limits
- Configure warning messages
- View blocked attempts
- View overrides
- Enable/disable rules

Example:

``` text
Habit: Doom Scrolling

Apps:
- Instagram
- Reddit
- YouTube

Level: Major
Daily limit: 45 minutes
Allowed time: 09:00 - 09:30
```

------------------------------------------------------------------------

# 4. Warning Levels

## Minor

Lowest friction.

Show warning when protected app opens.

Example:

``` text
Why are you opening this app?

You said you wanted to reduce scrolling.

[Continue]
```

Purpose:

- Awareness
- No hard blocking
- Log event

------------------------------------------------------------------------

## Major

Medium friction.

Show warning when protected app opens according to configured rule.

Example:

``` text
You are about to open Instagram.

You planned to avoid scrolling right now.

Reason:
"More focus. Less wasted time."

[Go Back] [Continue Anyway]
```

Possible rules:

- Every launch
- After configured usage duration
- During configured hours
- After daily limit warning threshold

User can skip.

Log every warning and override.

------------------------------------------------------------------------

## Max

Hard restriction.

Protected app cannot be opened after configured boundary.

Example:

``` text
Instagram blocked.

Allowed time:
09:00 - 09:30

Next available:
Tomorrow at 09:00

Reason:
"I want control over my time."

[Back]
```

Possible rules:

- Daily usage limit
- Allowed time windows
- Blocked time windows
- Maximum launches
- Maximum continuous usage

Max override must be deliberate.

Example:

``` text
Emergency unlock
        ↓
Confirmation
        ↓
Temporary unlock
        ↓
Override logged
```

Max bypass should be disabled by default during Focus mode.

------------------------------------------------------------------------

# 4. Smart Escalation

Support dynamic escalation.

Example:

``` text
0–20 min      → Minor
20–40 min     → Major
40+ min       → Max
```

Or:

``` text
Normal usage
    ↓
Repeated usage
    ↓
Major warning
    ↓
Limit exceeded
    ↓
Max restriction
```

Escalation rules must be configurable.

Do not hardcode escalation thresholds.

------------------------------------------------------------------------

# 5. Habit Model

Habit must be first-class domain concept.

Example:

``` text
Habit
└── Doom Scrolling
    ├── Instagram
    ├── Reddit
    └── YouTube
```

Another:

``` text
Habit
└── Gaming
    ├── PUBG
    ├── Steam
    └── Discord
```

One habit can contain multiple protected apps.

------------------------------------------------------------------------

# 6. Focus

Focus section provides concentration sessions.

User chooses:

``` text
Focus duration: 50 min
Break duration: 10 min
Cycles: 4
```

Rule:

``` text
breakDuration <= focusDuration
```

Valid:

``` text
50 min / 10 min
25 min / 25 min
20 min / 20 min
```

Invalid:

``` text
20 min / 30 min
```

Focus session states:

``` text
IDLE
FOCUSING
BREAK
PAUSED
COMPLETED
CANCELLED
```

UI:

``` text
FOCUS

42:31

██████████████░░░░

Session 1 / 4

Deep work mode
```

Actions:

- Start
- Pause
- Resume
- End
- Start break
- Skip break
- Complete session

Avoid making escape actions too easy.

------------------------------------------------------------------------

# 7. Focus + Guard Integration

Focus mode can bypass selected Guard restrictions.

Settings:

``` text
Focus Mode

☑ Ignore Minor warnings
☑ Ignore Major warnings
☐ Ignore Max restrictions
```

Recommended default:

``` text
Minor → bypass allowed
Major → bypass configurable
Max   → bypass disabled
```

Focus session must be recorded in Matrix.

------------------------------------------------------------------------

# 8. Matrix

Matrix is analytics dashboard.

Show:

## Daily

- Screen time
- Focus time
- Blocked attempts
- Warnings shown
- Overrides
- Protected-app usage
- Break time

## Weekly

- Focus hours
- Distracted hours
- Habit attempts
- Successful blocks
- Most opened protected app
- Most problematic time
- Longest focus session

## Monthly

- Focus trend
- Screen-time trend
- Habit reduction
- Consistency
- Goal completion
- Override trend

------------------------------------------------------------------------

# 9. Graph Types

Support multiple visualization types:

- Line chart
- Bar chart
- Area chart
- Pie chart
- Donut chart
- Radar chart
- Calendar heatmap
- Progress ring
- Stacked bar chart
- Timeline
- Scatter plot

Graphs must consume analytics domain data, not directly query Android
APIs.

------------------------------------------------------------------------

# 10. External / Web Data

Matrix can later consume external data.

Possible sources:

- Weather
- Google Calendar
- GitHub
- Fitness APIs
- Sleep data
- Study platforms
- Finance
- Productivity APIs

Architecture:

``` text
External API
    ↓
Data Source Adapter
    ↓
Normalized Data
    ↓
Analytics Engine
    ↓
Matrix
```

Keep external integrations isolated.

Do not couple Matrix UI directly to external APIs.

------------------------------------------------------------------------

# 11. Profile

Profile/settings section:

- User goals
- Warning library
- Custom warnings
- Notifications
- Theme
- Permissions
- Accessibility status
- Battery optimization status
- Data export
- Data import
- Backup
- Privacy settings
- App behavior settings

------------------------------------------------------------------------

# 12. Warning Library

Warnings must be data-driven.

Do not hardcode warning text inside UI code.

Suggested model:

``` kotlin
data class Warning(
    val id: String,
    val category: WarningCategory,
    val level: WarningLevel,
    val title: String,
    val message: String,
    val motivationalMessage: String,
    val enabled: Boolean
)
```

Categories:

``` text
DISCIPLINE
STUDY
FITNESS
SOCIAL_MEDIA
GAMING
PRODUCTIVITY
SLEEP
MONEY
CUSTOM
```

Users can:

- Add custom warning
- Edit warning
- Delete warning
- Favorite warning
- Enable/disable warning
- Randomize warning

------------------------------------------------------------------------

# 13. Android Background Architecture

Do not keep Activity alive for background monitoring.

Use Android system components.

Primary components:

``` text
AccessibilityService
ForegroundService
WorkManager
BroadcastReceiver
Room
```

Architecture:

``` text
                 Android System
                       │
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
Accessibility     Foreground       WorkManager
 Service           Service
        │              │              │
        └──────────────┼──────────────┘
                       ↓
                 Habit Engine
                       │
          ┌────────────┼────────────┐
          ↓            ↓            ↓
       Warning       Blocking     Analytics
          │            │            │
          └────────────┼────────────┘
                       ↓
                     Room
                       ↓
                    Matrix
```

------------------------------------------------------------------------

# 14. AccessibilityService

Use AccessibilityService as core mechanism for protected-app detection
and immediate reaction when supported by Android/device configuration.

Responsibilities:

- Detect foreground package
- Check whether package is protected
- Notify Habit Engine
- Trigger warning/restriction flow
- Detect relevant navigation changes
- Respect enabled Guard rules

Do not put business logic directly inside AccessibilityService.

Flow:

``` text
AccessibilityService
        ↓
ForegroundAppDetector
        ↓
HabitEngine
        ↓
RuleEngine
        ↓
RestrictionDecision
        ↓
Warning / Block UI
```

AccessibilityService must only handle platform-specific detection and
communication.

------------------------------------------------------------------------

# 15. Foreground Service

Use ForegroundService for ongoing monitoring/session work that needs
foreground execution.

Example:

``` kotlin
class HabitMonitorService : Service() {

    override fun onCreate() {
        super.onCreate()

        // Start monitoring
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null
}
```

Start:

``` kotlin
ContextCompat.startForegroundService(
    context,
    Intent(context, HabitMonitorService::class.java)
)
```

Service must call `startForeground()` according to Android requirements
and display persistent notification.

Do not assume `START_STICKY` guarantees permanent execution.

Android may stop or restrict processes.

------------------------------------------------------------------------

# 16. UsageStatsManager

Use UsageStatsManager for usage analytics and historical usage data.

Use it for:

- App usage duration
- Daily usage
- Historical statistics
- Usage aggregation
- Matrix analytics

Do not use it as only mechanism for instant app-open interception when
immediate reaction is required.

------------------------------------------------------------------------

# 17. BootReceiver

Restore required monitoring state after device reboot.

Manifest permission:

``` xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Receiver:

``` kotlin
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start required monitoring
        }
    }
}
```

Manifest:

``` xml
<receiver
    android:name=".BootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

Only restore required state. Do not perform heavy work inside
BroadcastReceiver.

------------------------------------------------------------------------

# 18. WorkManager

Use WorkManager for deferrable background work.

Good uses:

- Analytics aggregation
- Cleanup
- Database maintenance
- Backup preparation
- External API synchronization
- Periodic summaries

Do not use WorkManager as replacement for immediate app-open detection.

------------------------------------------------------------------------

# 19. Battery Optimization

Some Android manufacturers aggressively restrict background execution.

App should detect relevant restrictions and guide user.

Settings flow can point user toward:

``` text
Settings
→ Battery
→ Battery optimization
→ Allow app unrestricted usage
```

Do not silently modify device settings.

Explain why permission/restriction is needed.

------------------------------------------------------------------------

# 20. Important Android Constraint

Never promise:

``` text
"App can NEVER be killed"
```

Android controls process lifetime.

Design for resilience instead:

``` text
AccessibilityService
+
ForegroundService
+
WorkManager
+
BootReceiver
+
Room persistence
```

Persist all important state.

If process dies:

``` text
Process killed
    ↓
App restarts
    ↓
Load state from Room
    ↓
Restore monitoring
    ↓
Continue rules
```

------------------------------------------------------------------------

# 21. Suggested Package Structure

``` text
app/
├── core/
│   ├── database/
│   ├── datastore/
│   ├── network/
│   ├── notifications/
│   ├── permissions/
│   ├── analytics/
│   └── common/
│
├── data/
│   ├── local/
│   ├── remote/
│   ├── repository/
│   └── mapper/
│
├── domain/
│   ├── habit/
│   ├── warning/
│   ├── restriction/
│   ├── focus/
│   └── analytics/
│
├── feature/
│   ├── onboarding/
│   ├── guard/
│   ├── focus/
│   ├── matrix/
│   └── profile/
│
├── service/
│   ├── accessibility/
│   ├── monitoring/
│   └── boot/
│
└── MainActivity.kt
```

Architecture principle:

``` text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
Data Source
```

Domain layer must not depend on Android UI classes.

------------------------------------------------------------------------

# 22. Recommended Android Stack

Preferred:

``` text
Language: Kotlin

UI:
Jetpack Compose

Architecture:
Clean Architecture + MVVM

DI:
Hilt

Database:
Room

Preferences:
DataStore

Background:
WorkManager

Monitoring:
AccessibilityService
UsageStatsManager
ForegroundService

Navigation:
Navigation Compose

Async:
Kotlin Coroutines + Flow

Serialization:
kotlinx.serialization

Charts:
Compose-compatible chart library

Testing:
JUnit
AndroidX Test
Compose UI Test
```

Use latest stable versions available when project is created.

Do not blindly copy dependency versions from this document.

------------------------------------------------------------------------

# 23. Core Domain Models

Suggested models:

``` kotlin
data class Habit(
    val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean
)
```

``` kotlin
data class ProtectedApp(
    val packageName: String,
    val habitId: String,
    val warningLevel: WarningLevel,
    val enabled: Boolean
)
```

``` kotlin
enum class WarningLevel {
    MINOR,
    MAJOR,
    MAX
}
```

``` kotlin
data class RestrictionRule(
    val id: String,
    val habitId: String,
    val dailyLimitMinutes: Int?,
    val allowedStartMinutes: Int?,
    val allowedEndMinutes: Int?,
    val maxLaunches: Int?,
    val warningLevel: WarningLevel
)
```

``` kotlin
data class FocusSession(
    val id: String,
    val focusDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val cycles: Int,
    val startedAt: Long?,
    val completedAt: Long?,
    val status: FocusSessionStatus
)
```

``` kotlin
enum class FocusSessionStatus {
    IDLE,
    FOCUSING,
    BREAK,
    PAUSED,
    COMPLETED,
    CANCELLED
}
```

------------------------------------------------------------------------

# 24. Event Logging

Everything important should become an event.

Examples:

``` text
APP_OPENED
WARNING_SHOWN
APP_CONTINUED
APP_BLOCKED
OVERRIDE_USED
FOCUS_STARTED
FOCUS_PAUSED
FOCUS_RESUMED
FOCUS_COMPLETED
FOCUS_CANCELLED
BREAK_STARTED
BREAK_COMPLETED
```

Suggested event model:

``` kotlin
data class HabitEvent(
    val id: String,
    val type: HabitEventType,
    val packageName: String?,
    val habitId: String?,
    val timestamp: Long,
    val metadata: Map<String, String>
)
```

Events power Matrix.

------------------------------------------------------------------------

# 25. Rule Engine

Keep restriction decisions centralized.

Example:

``` text
Current App
    ↓
Protected?
    ↓
Current Habit
    ↓
Current Usage
    ↓
Current Time
    ↓
Focus Active?
    ↓
Restriction Rules
    ↓
RestrictionDecision
```

Suggested result:

``` kotlin
sealed interface RestrictionDecision {

    data object Allow : RestrictionDecision

    data class ShowMinorWarning(
        val warningId: String
    ) : RestrictionDecision

    data class ShowMajorWarning(
        val warningId: String
    ) : RestrictionDecision

    data class Block(
        val reason: String
    ) : RestrictionDecision
}
```

Do not spread rule logic across UI, services, and repositories.

------------------------------------------------------------------------

# 26. Matrix Architecture

Matrix must consume normalized domain data.

``` text
Raw Events
    ↓
Analytics Aggregator
    ↓
Metrics
    ↓
Chart Data
    ↓
Matrix UI
```

Example:

``` kotlin
data class DailyMetrics(
    val date: LocalDate,
    val focusMinutes: Int,
    val distractionMinutes: Int,
    val blockedAttempts: Int,
    val warningCount: Int,
    val overrideCount: Int
)
```

UI should receive chart-ready data.

------------------------------------------------------------------------

# 27. Home Dashboard

Guard home should show current state, not only configuration.

Example:

``` text
TODAY

Discipline
████████████████░░░░ 82%

Focus
3h 24m

Screen Time
2h 11m

Blocked Attempts
7

Habit Progress
██████████████░░ 78%

[Start Focus]
```

Keep dashboard simple.

------------------------------------------------------------------------

# 28. Notifications

Use notifications for:

- Focus session completion
- Break completion
- Focus session start
- Daily summary
- Important restriction status
- Permission problems
- Monitoring disabled

Do not spam notifications.

Allow notification preferences.

------------------------------------------------------------------------

# 29. Permissions

Explain every permission before requesting it.

Potential permissions/capabilities:

- Accessibility service
- Usage access
- Notifications
- Foreground service
- Exact alarm only if truly required
- Boot completed
- Battery optimization guidance

Never request unnecessary permissions.

------------------------------------------------------------------------

# 30. Privacy

Behavior data is sensitive.

Default architecture:

``` text
Device
  ↓
Room
  ↓
Local analytics
```

Cloud sync should be opt-in.

Do not send app usage data externally without explicit user choice.

External integrations must clearly state what data leaves device.

Provide:

- Export data
- Delete data
- Disable analytics
- Disable integrations
- Clear history

------------------------------------------------------------------------

# 31. Security

Protect:

- User settings
- Habit rules
- Usage history
- External API tokens
- Backup data

Never store secrets in source code.

Use Android Keystore for sensitive credentials where appropriate.

Do not log sensitive user behavior unnecessarily.

------------------------------------------------------------------------

# 32. UX Principles

Warnings should create pause, not annoyance.

Avoid:

- Fake emergency messages
- Manipulative wording
- Shame
- Excessive notifications
- Infinite blocking loops
- Impossible recovery paths

Good warning:

``` text
You planned to avoid this app right now.

Take 10 seconds.

[Go Back] [Continue]
```

Bad warning:

``` text
You failed again.
You are wasting your life.
```

------------------------------------------------------------------------

# 33. Recovery / Safety

User must always be able to recover from accidental configuration.

Examples:

- Disable rule from Guard
- Temporary override
- Emergency unlock
- Reset restrictions
- Safe onboarding defaults

Max should never create irreversible device lockout.

------------------------------------------------------------------------

# 34. Onboarding

First launch:

``` text
Welcome
   ↓
Choose goal
   ↓
Select habits
   ↓
Select protected apps
   ↓
Choose warning level
   ↓
Configure limits
   ↓
Grant required permissions
   ↓
Test protection
   ↓
Dashboard
```

Onboarding should explain why AccessibilityService and Usage Access are
required.

------------------------------------------------------------------------

# 35. MVP Scope

Build first:

``` text
1. Bottom navigation
2. Guard
3. Protected app selection
4. Minor warnings
5. Major warnings
6. Max restrictions
7. Custom warning messages
8. Focus timer
9. Break timer
10. Basic event logging
11. Basic Matrix
12. Room database
13. AccessibilityService
14. ForegroundService
15. Boot recovery
16. Permission setup
```

Do not build external APIs in MVP.

------------------------------------------------------------------------

# 36. V2

Add:

``` text
Habit grouping
Smart escalation
Advanced analytics
More graph types
Goals
Streaks
Focus presets
Detailed history
Data export
Improved notifications
```

------------------------------------------------------------------------

# 37. V3

Add:

``` text
External APIs
Cloud backup
Cross-device sync
AI-generated insights
Correlation engine
Advanced recommendations
Custom analytics
```

------------------------------------------------------------------------

# 38. Future AI Layer

AI must not control restrictions directly.

AI can analyze data:

``` text
Matrix
  ↓
Analytics
  ↓
AI Insight Engine
  ↓
Human-readable insight
```

Example:

``` text
"You tend to spend more time on protected apps
between 22:00 and 00:00."

"Your average focus duration increased
after reducing social-media usage."
```

AI should explain observations using recorded data.

Rules remain deterministic.

------------------------------------------------------------------------

# 39. Testing Strategy

Test domain logic independently.

Important tests:

``` text
Minor rule
Major rule
Max rule
Daily limit
Allowed hours
Blocked hours
Launch limit
Focus bypass
Max bypass
Escalation
Break <= focus validation
Override logging
Process restart recovery
Boot recovery
Analytics aggregation
```

Example:

``` text
Given:
focusDuration = 20
breakDuration = 30

Expected:
Validation fails
```

------------------------------------------------------------------------

# 40. Development Rules

Agents must:

1. Keep business logic out of Activities.
2. Keep business logic out of AccessibilityService.
3. Keep business logic out of BroadcastReceiver.
4. Use repositories for data access.
5. Use use cases for business operations.
6. Keep Android-specific code isolated.
7. Persist important state.
8. Avoid unnecessary background loops.
9. Prefer event-driven behavior.
10. Avoid polling when Android API can provide required information.
11. Keep warning content data-driven.
12. Keep restriction rules centralized.
13. Write tests for rule-engine changes.
14. Never hardcode package names.
15. Never hardcode warning thresholds.
16. Never hardcode user-specific settings.
17. Do not add permissions without feature justification.
18. Do not add external network dependency to MVP.
19. Do not expose sensitive usage data in logs.
20. Keep UI responsive and lifecycle-aware.

------------------------------------------------------------------------

# 41. Git Rules

Use small commits.

Recommended format:

``` text
feat: add habit guard
feat: add major warning
feat: add max restriction
feat: add focus timer
feat: add matrix analytics
fix: restore monitoring after reboot
fix: prevent invalid focus break duration
refactor: move restriction rules into domain
test: add habit engine tests
```

Do not mix unrelated changes in one commit.

------------------------------------------------------------------------

# 42. Definition of Done

Feature is complete only when:

``` text
Code implemented
+
Unit tests added
+
UI state handled
+
Process death considered
+
Permission failure handled
+
Persistence handled
+
Error state handled
+
Accessibility failure handled
+
Battery restriction considered
+
Analytics event recorded
```

------------------------------------------------------------------------

# 43. Main Architecture Summary

``` text
                         Android System
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ↓                   ↓                   ↓
 AccessibilityService   ForegroundService    WorkManager
          │                   │                   │
          └───────────────────┼───────────────────┘
                              ↓
                         App Monitor
                              ↓
                         Habit Engine
                              ↓
                         Rule Engine
                              ↓
                  ┌───────────┼───────────┐
                  ↓           ↓           ↓
               Allow       Warning      Block
                              │
                              ↓
                         Event Logger
                              ↓
                             Room
                              ↓
                     Analytics Engine
                              ↓
                           Matrix

UI:

Guard ─────────────┐
Focus ─────────────┤
Matrix ────────────┼──→ ViewModels → UseCases → Repositories
Profile ───────────┘
```

------------------------------------------------------------------------

# 44. Golden Rule

App exists to create:

``` text
FRICTION
   ↓
AWARENESS
   ↓
CHOICE
   ↓
ACTION
   ↓
MEASUREMENT
   ↓
IMPROVEMENT
```

Do not turn app into punishment system.

Make user control explicit.

Make rules predictable.

Make data transparent.

Make recovery possible.
