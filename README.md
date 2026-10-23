# LOCKDOWN — Unbreakable Focus. No Way Out.

A standalone, offline-first Android app whose only job is to make app & website
blocking **unbreakable** while Strict Mode is running. You pick what to block,
activate Strict Mode for a set duration, and until the timer ends you cannot
edit the lists, open Settings, remove the app, or stop it early.

- Package: `com.lockdown.strictblocker`
- Kotlin 100%, Jetpack Compose UI, Room database
- Min SDK 26 (Android 8.0), Target SDK 34 (Android 14)
- No ads, no trackers, no accounts, no backend. Everything stays on-device.
- Theme: "Obsidian Vault" — deep black `#0A0A0A`, charcoal `#1C1C1E`, white,
  and a single accent, Electric Lime `#CCFF00`.

> LOCKDOWN is an original implementation built on public Android APIs
> (Accessibility Service, UsageStats, VpnService, DevicePolicyManager).
> It is not affiliated with any other blocking app.

---

## What it does

### Blocking engine
- **App blocking** — any installed app can be blocked from launching.
- **Blacklist / Whitelist modes for apps** — block only the apps you pick, or
  block *every* app except the ones you pick (your launcher always stays
  allowed, and system dialogs/keyboards can never be blocked).
- **Website blocking** — a local VPN filters DNS lookups on-device; blocked
  domains resolve to `0.0.0.0`. Domains you add (e.g. `youtube.com`) also
  cover subdomains (`m.youtube.com`).
- **Blacklist / Whitelist modes for websites** — block listed sites, or allow
  *only* listed sites.
- **Keyword blocking** — the Accessibility Service reads browser address bars;
  if a URL contains one of your keywords (`news` blocks `cnn.com/news`), the
  page is covered by the blocking screen.
- **Full-screen blocking screen** — black vault door, breathing lime padlock,
  the reason, and the live Strict Mode countdown. No close button.

### Strict Mode
- Durations: 15 min, 30 min, 1 h, 6 h, 24 h, or custom up to 99 hours.
- The deadline is stored as an absolute timestamp: reboots and process death
  don't shorten it. A `BOOT_COMPLETED` receiver re-arms protection.
- While active:
  - Block lists are frozen (the UI refuses edits).
  - The system Settings app is guarded by the overlay, so LOCKDOWN's App
    info, Accessibility and Device Admin screens can't be reached.
  - A persistent, non-dismissible notification shows the time remaining.
  - A running session can be *extended* but never shortened.
- Strict Mode ends only when the timer expires.

### Vault PIN
- Forced 4-digit PIN creation on first launch.
- The Settings tab, PIN changes and uninstall steps sit behind the PIN.
- Stored as a salted SHA-256 hash in `EncryptedSharedPreferences`
  (AES-256) — never in plain text. No biometric bypass.
- 5 wrong attempts → 1-minute lockout.
- **Forgot PIN** works only when Strict Mode is *not* active: answer your
  recovery question, or request a reset and wait 24 hours.

### Protection levels (honest version)
Android decides how deep an app's protection can go:

| Level | What you get |
|---|---|
| Permissions only | Blocking works, but a determined user can uninstall LOCKDOWN. |
| + Device Admin (in-app toggle) | Uninstall friction + a warning if someone tries to disable the admin mid-session; Settings is guarded while Strict Mode runs. |
| + Device Owner (ADB, below) | Maximum: hard uninstall blocking (`setUninstallBlocked`-class protection), the closest thing to truly unbreakable Android allows. |

No app without Device Owner can survive a factory reset or safe-mode boot
with admin removal — that's an Android platform rule, not an app shortcut.

---

## Build the APK

The project is a standard Android Studio project. Two ways to build:

### Option A — GitHub Actions (no Android Studio needed)
A workflow is included at `.github/workflows/build-apk.yml`:

1. Push this folder to a GitHub repo.
2. Open **Actions → "Build debug APK + AAB" → Run workflow**.
3. Download the **`Lockdown-debug-apk`** artifact, copy it to your phone and
   install it (allow "install unknown apps" when prompted). The AAB artifact
   is also produced.

The debug build is debug-signed and installs fine for personal use. For a
Play Store release you would need your own signing key, a release build, and
a target-SDK bump per current Play policy.

### Option B — Android Studio
Open the folder, let Gradle sync, then **Build → Build APK(s)**.
(If Gradle complains there's no wrapper, run `gradle wrapper --gradle-version 8.7`
once — AGP 8.5.x needs Gradle 8.7+.)

---

## First-run setup (in the app)

1. **Create your 4-digit vault code** (+ optional recovery question).
2. **Usage Access** — lets LOCKDOWN see which app is in front.
3. **Accessibility Service** — the blocking engine itself.
4. **Display Over Apps** — permission to draw the blocking screen.
5. **Device Admin** — uninstall protection.
6. **Device Owner (optional)** — the ADB step below.
7. **Battery exemption** — so Android doesn't put the guard to sleep.

## Device Owner via ADB (optional, maximum protection)

Android only grants Device Owner through ADB, and only on a device with **no
accounts set up** (fresh device, or after a factory reset / removing all
accounts). With the phone connected to a computer with platform-tools:

```
adb shell dpm set-device-owner com.lockdown.strictblocker/.engine.AdminReceiver
```

The app shows this command with a copy button during setup and in Settings.
To remove Device Owner later:

```
adb shell dpm remove-active-admin com.lockdown.strictblocker/.engine.AdminReceiver
```

## Permissions explained

| Permission | Why |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Detect foreground app launches (backup to Accessibility). |
| Accessibility Service | Primary blocking: app switches + browser URL/keyword checks + Settings guard during Strict Mode. |
| Display Over Apps (`SYSTEM_ALERT_WINDOW`) | Draw the full-screen blocking overlay. |
| VPN Service (`BIND_VPN_SERVICE`) | Local, DNS-only VPN for website blocking. Routes only DNS server addresses; all other traffic is untouched and nothing is logged. |
| Device Admin | Uninstall friction and disable-warnings. |
| Device Owner (optional, ADB) | Hard uninstall blocking. |
| Foreground Service + Notifications | The persistent "LOCKDOWN is Active" status and always-on monitoring. |
| Boot Completed | Re-arm blocking after a reboot. |
| Battery Optimization Exemption | Keep the guard running. |
| Query Installed Apps | Show your app list in the picker. |

## How to uninstall

1. Wait for Strict Mode to end (nothing can remove LOCKDOWN mid-session).
2. Open **Settings tab → enter PIN → Deactivate Device Admin**.
3. Uninstall normally. (Device Owner: run the `remove-active-admin` ADB
   command above first.)

## Notes & limits

- DNS blocking works at domain level; full URL paths inside HTTPS are
  encrypted, which is why keyword/full-URL rules are enforced through the
  browser address bar instead. Both layers are on by default.
- Whitelist app mode only polices apps that have a launcher entry — by
  design, so keyboards, permission dialogs and the dialer keep working.
- Fonts are mapped to the platform's grotesque/monospace families; to bundle
  Inter and JetBrains Mono, drop the TTFs into `res/font` and point
  `UiFont`/`MonoFont` in `ui/Theme.kt` at them.
