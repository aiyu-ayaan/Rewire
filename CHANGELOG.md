# Changelog

## [1.0.1-alpha.7](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.6...v1.0.1-alpha.7) (2026-10-03)

### Features

* Show limit summary under protected apps instead of generic label
* Add summary parts formatting for AppLimits
* Set an app's own daily limit, launch limit or window from the habit screen
* Store per-app limit overrides (Room schema v4)
* Judge each app by its own limits when it has them
* Add per-app limits that replace the habit's boundaries for one app
* Show streak and goal progress on Guard and Matrix daily
* Add daily goals editor to Profile
* Add escalation settings to habit detail
* Query usage for escalation and explain escalation blocks
* Add focus preset chips with save, rename and delete
* Persist goals in a DataStore and include them in backups
* Add daily goals and streak domain
* Persist escalation tiers in Room (schema v3)
* Add smart escalation tiers to rule engine
* Include user focus presets in backup snapshot
* Persist user focus presets in DataStore behind a repository
* Add focus preset domain model with validation and naming rules
* Gate self-update code paths behind BuildConfig.UPDATES
* Add daily, weekly and monthly Matrix views with period switch
* Add bar, line, stacked, radar, scatter, heatmap and timeline charts with table alternative
* Add chart-ready data classes and domain-to-chart mapping
* Add export, import and clear history to Profile
* Post daily summary notification from a daily worker
* Add daily summary use case
* Add backup repository for export, import and clear history
* Add versioned backup snapshot with validating JSON codec
* Add weekly and monthly metrics aggregator
* Improve run script to handle flavor and log options more effectively
* Count daily limit usage only inside the allowed window
* Compute where window-scoped daily limit usage starts

### Bug fixes

* Deactivate implicit zen rules and ensure interruption filter ALL on call and startup
* Ensure call ringer and vibration remain active during ringing and active calls
* Silence notifications via stream volume so calls ring and vibrate normally
* Do not intercept incoming or active phone and VoIP calls in HabitEngine
* Allow phone and VoIP calls through Focus DND policy
* Judge daily limit, escalation and launch limit per app instead of per habit
* System back on the focus result screen returns to setup
* Wake the focus ticker as soon as the UI collects again so rotation never freezes the countdown
* Hide new-habit button over the empty state and cap discipline ring label at large font
* Space goal chips from the screen time card in Matrix daily
* Pluralize counts in the import confirmation
* TalkBack labels, roles, headings and no per-second timer announcements
* Stack, wrap and scroll layouts at large font scale; read locale observably in Matrix weekly
* Cap content width on wide windows and let landing scroll in landscape
* Use PRIORITY_SENDERS_ANY constant for DND policy message senders

### Other changes

* Per-app limit overrides, call passthrough in focus, smart escalation, and matrix charts
* Adapt warning screen to system theme using semantic color tokens
* Let each boundary be overridden per app on its own
* Mark emulator findings as fixed
* Record emulator test results in tracker
* Record accessibility audit in tracker
* Update development tracker for phases 2, 4, 5, 6 and play build
* Add Room 2 to 3 migration test for escalation columns
* Move goal labels and reason quotes to strings.xml
* Move update copy to strings.xml
* Move onboarding and landing copy to strings.xml
* Move permissions and warning library copy to strings.xml
* Move profile and about copy to strings.xml
* Move focus copy to strings.xml
* Move guard copy to strings.xml
* Move matrix and navigation copy to strings.xml
* Document play flavor without self-update
* Add play flavor without self-update permissions
* Add Room 1 to 2 migration test
* Cover weekly and monthly metrics
* Regenerate baseline profiles for com.aiyu.rewire package
* Rename package declarations and imports to com.aiyu.rewire
* Update package name to com.aiyu.rewire in build config and docs

## [1.0.1-alpha.6](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.5...v1.0.1-alpha.6) (2026-10-02)

### Bug fixes

* No flash before the block screen in Lite

### Other changes

* Lite block screen opens without a flash

## [1.0.1-alpha.5](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.4...v1.0.1-alpha.5) (2026-10-02)

### Bug fixes

* Warn when Lite can't watch, read usage events further back
* Re-judge the open app after unlock, restart monitoring from the app
* Block Max apps in Lite when the guard start is refused
* Judge every usage resume in Lite, not only the newest

### Other changes

* Lite blocks Max apps reliably (overlay shield, every open judged, re-check after unlock, protection-off warnings)
* Log the remaining Lite blocking fixes
* Log the Lite blocking fix

## [1.0.1-alpha.4](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.3...v1.0.1-alpha.4) (2026-10-02)

### Features

* Landscape layouts and sensor rotation for the timer
* Explain Full vs Lite build in About, link the install guide
* Show Full or Lite build in About and Updates
* Add Lite build without an accessibility service
* Detect protected apps via usage access when accessibility is off

### Bug fixes

* Keep the block screen's content out of Recents
* Restart monitoring after an app update
* Create HabitEngine at app start so Lite starts monitoring

### Other changes

* Rewire Lite (installs past Play Protect, works with payment apps), landscape focus timer
* Flavors guide, switching, testing; log today's decisions
* Fail unless both APKs ship and Lite has no accessibility service
* Explain Full vs Lite APK and the Play Protect / payment-app fix
* Ship Full and Lite APKs; updater keeps each on its flavor

## [1.0.1-alpha.3](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.2...v1.0.1-alpha.3) (2026-10-01)

### Features

* Show per-app usage today under protected apps

### Bug fixes

* Minor and major warn only once a boundary is crossed
* Refresh today's usage live on resume and every minute
* Compute today's usage from UsageEvents instead of overlapping stat buckets

### Other changes

* Accurate Digital Wellbeing usage, per-app usage, and warnings only at boundaries
* Describe when minor and major warnings appear
* Cover usage event aggregation

## [1.0.1-alpha.2](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.1-alpha.1...v1.0.1-alpha.2) (2026-10-01)

### Features

* In-app updates from GitHub releases
* Release rules and Room schema v2 for update prefs
* Add achievement note and focus history
* Pop up when leaving mid-session and show a Live Update
* Move all persistence to a Room database
* Chime when break ends and focus resumes
* Add debug-only 20s/10s quick test session
* Play bundled break chime when focus ends
* Add interactive protection test and align permission count
* Add usage access permission guidance hint to daily limit settings
* Display user profile personal reason on warning and block screens
* Play audio tone on break start and add 1-tap notification mute for blocked apps
* Silence all messages except calls during focus mode
* Sync digital wellbeing usage stats for accurate time-based tracking
* Add android 13+ restricted settings guidance and deep link
* Add foreground service and system alert window for reliable background blocking

### Bug fixes

* Silence only notifications during focus DND
* Run the timer in a foreground service
* Judge the open app when Guard reconnects after process death
* Guard screen never silently lost, so blocked apps stay blocked
* Only System UI recents ends a visit, not shade or volume panel
* Re-judge protected app opened from recents over block screen
* Prevent launch limit bypass via recents screen
* SilenceNotification works for all blocked habits, use consistent clock
* Enforce launch/daily/window limits for all warning levels
* Suppress conversations and restore DND on app start
* Remove high-risk permissions and network dependency to prevent play protect blocking

### Other changes

* Room database, foreground focus timer, focus history, Hilt and in-app updates
* Wait for onboarding or main app in benchmark setup
* Add baseline profile module and generated startup/journey profile
* Replace AppContainer with Hilt
* Add Hilt 2.60.1 (works with AGP 9 built-in Kotlin)
* Update tracker and architecture for Room and focus service
* List Room and WorkManager in acknowledgements
* Add Room with KSP and schema export
* Update tracker for guard hardening
* Drop unused accessibility service instance handle
* Add WorkManager dependency for deferred background work
* Add comprehensive unit tests for habit engine outcome flows
* Update Todo roadmap with completed background resilience and wellbeing tracking

## [1.0.1-alpha.1](https://github.com/aiyu-ayaan/Rewire/compare/v1.0.0...v1.0.1-alpha.1) (2026-10-01)

### Features

* Show full-app matrix with guard stats, punchlines and show-all lists
* Add area, donut, heatmap and meter charts for matrix
* Add guard breakdown, peak hour and punchlines to analytics
* Expand built-in warning library and merge new defaults into existing libraries
* Fetch developer avatar from GitHub, move acknowledgements to own screen
* Add about screen with developer info and open-source licenses
* Add animated pause-to-turn splash screen
* Replace launcher and notification icons with rewire mark
* Enforce guard rules with auth-gated emergency unlock and notification blocking
* Persist habits, warnings and events across process death
* Add central rule engine for restriction decisions
* Show user profile card and permissions in profile tab
* Add profile and permissions steps to onboarding
* Add permissions panel with live status and settings shortcuts
* Declare accessibility service and usage access for guard detection
* Hide bottom navigation outside tab base screens
* Add fullscreen amoled focus timer with rolling digits
* Add basic matrix and profile with notification settings
* Add focus timer with ongoing notification and bypass settings
* Add guard dashboard, habit editor and warning previews
* Add landing hero with shared transition and adaptive app shell
* Add notification channels, notifier and permission flow
* Add in-memory repositories, settings store and app container
* Add habit, warning, focus and analytics domain models
* Add material 3 expressive theme with brand tonal schemes

### Bug fixes

* Use expressive large app bar on profile and permissions screens

### Other changes

* First signed pre-release of rewire
* Add marker-driven release workflow with release PR
* Add release version, notes and keystore decode scripts
* Derive version from VERSION file and sign from env
* Sign release from local keystore.properties
* Enable R8 and resource shrinking for release
* Add 9:16 launch video and playable gif to README
* Add MIT license and logo to README
* Add README with architecture and app screenshots
* Add final rewire mark and usage guide
* Add logo concept explorations
* Add .gitignore for CodeGraph data files and create skills-lock.json for material-3 skill
* Track guard enforcement
* Track onboarding profile and permissions
* Track focus fullscreen and nav bar rules
* Mark phase 1 complete and log decisions
* Cover focus state machine, warning picker and daily metrics
* Pin material3 1.5 alpha for expressive apis, add datastore and shapes
* Add development tracker, roadmap and design docs
* Initialize empty android project starter

