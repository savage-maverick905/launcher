# Andromeda (Hie Technologies) — Phase 2

Package: com.hietech.andromeda | minSdk 26 | target 34 | Kotlin + Compose

## Build the APK
1. Create a GitHub repo, push this folder to `main`.
2. Open the **Actions** tab > **Android CI** (or run it manually via *Run workflow*).
3. When green, open the run, scroll to **Artifacts**, download **Andromeda-debug-apk**, unzip, install the APK.
4. Press Home and choose Andromeda (or Settings in-app > Set as default launcher).

## Status (not yet compiled or tested)
Phase 1: launcher registration, app discovery/launching, A-Z library + search, hide/restore, dock, Hie theme.
Phase 2 (partial): swipeable home pages (add pages in Settings), folders, add-to-home from library long-press,
accent colour + glass intensity (applied live, persisted).
Also Phase 2: Android widget hosting (add via long-press/swipe-down action or the picker; remove with the x button), configurable long-press and swipe-down gestures, gear button in library.
Not done: widget resizing, drag-and-drop layout (placement is via menus),
icon packs, real blur, Poppins font (SansSerif fallback), PIN for hidden apps. Phases 3-5 untouched.

Phase 3 (first slice): focus profiles (Study/Work/Personal/Night/Custom) with per-profile blocked apps, manual activation,
daily schedule (start/end, overnight ok), active-profile pill on home, 5-minute emergency override. Enforcement is
launcher-only (Andromeda refuses to launch blocked apps); it is evaluated at launch time, no background service.
Not done: usage statistics, Shizuku freezing, PIN-protected hidden apps, app locking.

Phase 3 (second slice): PIN-protected hidden-apps list (salted SHA-256; guards the list inside Andromeda only),
today's top-app usage in the Focus screen (needs Usage access granted in system settings; read once on screen open).
Not done: Shizuku freezing, app locking, scheduled usage limits. Phase 4 (Universe bridge) not started.

Phase 4 (first slice): UniverseBridge interface + intent implementation, user-supplied package/scheme in Settings,
docs/UNIVERSE.md. Auth, shared themes/settings, widgets and search integration are not implemented.
Phase 5 (device testing/optimisation) cannot be done by me; it needs your itel A90.
