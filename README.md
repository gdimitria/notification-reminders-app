# Notification Reminders

An Android app with two independent parts:

1. **Reminders**: keeps re-alerting you, with sound and vibration, about missed calls, SMS and notifications of the apps you choose, until you open or dismiss them.
2. **Sound profiles**: switches the phone between Loud, Normal, Silent and your own profiles with one tap.

## Features

- **Missed calls and SMS/MMS** as built-in sources: detected from the notifications of the default phone and messaging apps, with no call-log or SMS permission.
- **App selection**: pick any other installed app (WhatsApp, Viber, email, calendar, etc.) from a searchable list.
- **Reminder profiles** such as Day and Night, each with a schedule and, per type (missed calls, SMS and MMS, apps), no reminder, the phone's sound and vibration, common settings or custom settings.
- **Repeat period**: every 10 seconds up to every 2 hours.
- **Alert duration**: each reminder plays for 1–60 seconds.
- **Total reminding time**: stop after 1 minute up to 24 hours, or keep going until the notification is dismissed.
- **Maximum number of reminders**: 1–100 or unlimited.
- **Vibration type**: short, long, double, triple, heartbeat, SOS, rising or continuous, with adjustable intensity on supported devices.
- **Sound**: any system notification, alarm or ringtone sound, or any audio file. Adjustable volume, choice of audio channel (notification, alarm, ringtone, media) and an option to temporarily override the system volume.
- **Screen**: lights up the screen at each reminder.
- **Flash**: blinks the camera flash at each reminder (short, long, double, strobe or SOS).
- **LED**: blinks the notification LED in a chosen colour while reminders are pending (devices with a notification LED only).
- **Silent mode and Do Not Disturb**: respected by default, configurable.
- **Status notification** with *Stop all* and *Snooze 30 min* actions, optional stop on phone unlock.
- English and Greek user interface.

## Reminder profiles

A reminder profile (for example Day or Night) has a name, an on/off switch, a schedule and, for each of the three types **Missed calls**, **SMS and MMS** and **Apps**, one of four choices:

| Choice | Effect |
| --- | --- |
| No reminder | The type is not reminded about in this profile |
| Phone's sound and vibration | The phone's own notification sound at the phone's volume, following ring, vibrate and silent mode. Repeats as set in the common settings. |
| Common settings | One set of reminder settings shared by the types of the profile |
| Custom settings | Settings for this type only |

Rules:
- A profile with a schedule applies inside its time windows (any number, each with its own days, midnight crossing allowed). The first enabled match wins.
- A profile without a schedule is the fallback for all other times.
- **Active profile** on the home screen switches between *Automatic by schedule* and a profile chosen by hand.
- A pending notification whose type is off in the active profile waits for the next profile that reminds about it, with limits counted from that moment.
- The apps of the Apps type are chosen once, on the home screen, and shared by all profiles.
- Profiles can be added, and deleted from the home screen or from the profile screen.
- **Quiet on Wi-Fi networks**: part of every set of reminder settings (the common settings and each custom settings of a profile), so each can have its own Wi-Fi exception or none. Per network: all notifications or chosen sources (for example a door sensor app at home). Notifications that arrive on such a network are never reminded. Reminders that started elsewhere pause while connected and continue after leaving. *Phone's sound and vibration* follows the common settings' networks. Network names need location access (see Wi-Fi networks).

A fresh install starts with one reminder profile, Day (no schedule, common settings for all types). Add more profiles, such as Night, as needed. Settings of earlier versions are converted automatically, and the Night profile that versions 3–7 created is removed once unless it was edited.

## Sound profiles

Sound profiles set the phone's own sound settings and have nothing to do with the reminders. Tap a profile on the home screen, or use the **Sound profiles** quick settings tile, which switches to the next profile on each tap.

**Status bar**: with **Show in status bar** (on by default) the selected sound profile's icon appears at the top of the screen, with the notification icons: a loud speaker for Loud, a quiet speaker for Normal, a muted speaker for Silent, and the first letter or a chosen icon for your own profiles. Pulling down the shade shows the sound profile, the lock state and the active reminder profile, with buttons to switch sound profile (hidden while locked). Android keeps the right side of the status bar (Wi-Fi, battery) for the system, so apps cannot place icons there. The notification cannot be cleared: if *Clear* or a swipe removes it anyway (Android 14 and later allow this), the app puts it back at once. Switch off *Show in status bar* to hide it. On Samsung phones, make sure *Settings › Notifications › Status bar* shows notification icons.

Each profile sets, or leaves unchanged:
- **Ringer mode**: sound, vibrate or silent.
- **Volumes** of ring, notifications, media, alarms, system and calls (0–100%, each one optional).
- **Vibrate for calls** and **touch vibration** (on, off or unchanged).
- **Do Not Disturb**: off, priority only, alarms only, total silence or unchanged.

The app starts with three profiles, Normal selected:

| Profile | Ringer | Volumes | Vibration | Do Not Disturb |
| --- | --- | --- | --- | --- |
| Loud | sound | all 100% | vibrate for calls on | off |
| Normal | sound | ringtone 14%, notifications 13%, media 20%, system 0%, alarm 46%, in-call unchanged | vibrate for calls on, touch vibration off | off |
| Silent | silent | unchanged | vibrate for calls off | unchanged |

You can edit and delete these profiles and add your own.

### Wi-Fi networks

A sound profile can be applied automatically when the phone connects to a specific Wi-Fi network (*Add Wi-Fi network* in the Sound profiles card):
- Choose the network (type its name, tap *Use current network*, or *Choose from list*) and the sound profile. The list shows the network connected now, networks the phone connected to while the app was installed, and networks in range. Android does not let apps read the phone's list of saved networks, so the app keeps its own list.
- **While connected**: *Lock this profile* keeps the sound settings locked while the phone is on the network (unlocked when it leaves, and you can still unlock in the app), or *Allow changing by hand* applies the profile once on connecting and then lets you change profiles freely.
- **When the phone leaves the network**, the sound profile that was selected before connecting is applied again.
- The profile is applied only when the phone first connects, not again while it stays connected.
- Android reveals Wi-Fi network names only to apps with location access: grant *Location access*, choose *Allow all the time* (so it works while the app is closed) and keep location services on. The app does not use your location.
- The status bar notification names the Wi-Fi rule in force.

### Contact exceptions

Chosen contacts and contact groups can use their own sound profile instead of the current one (*Add contact exception* in the Sound profiles card), for example Loud for family while the phone is on Silent:
- **Incoming calls**: the app switches to the exception's sound profile before the phone rings and restores the previous settings when the call ends. This needs the app to be the phone's *Caller ID and spam app* (Android 10 and later). Calls are never blocked.
- **Messages and app notifications**: SMS and notifications of the selected apps from these contacts get an alert of their own with the exception's sound profile, because the phone plays its own sound before any app can change the settings.
- **Reminders** set to *Phone's sound and vibration* use the exception's sound profile for notifications from these contacts, instead of the phone's current settings.
- Contacts are recognised by phone number, contact or display name, as far as the notification reveals them. Contact groups (labels) are read from the Contacts app.
- Needs *Contacts access*. With *Lock also applies to this app* on, exceptions do not change the sound while locked.

### Sound lock

**Lock sound settings** (off by default) keeps the current ringer mode, all volumes, vibrate for calls, touch vibration and Do Not Disturb until you unlock them in the app:
- Android does not let apps disable the volume controls, so every change made elsewhere (volume buttons, phone settings, quick settings, other apps) is undone immediately.
- With **Block volume buttons while locked** (an accessibility service, switched on in the phone's accessibility settings) the volume buttons do nothing at all while locked.
- **Lock also applies to this app** (off by default):
  - Off: the app's own actions still work while locked. Applying a sound profile moves the lock to that profile, and reminders with *Override system volume* raise the volume while they play and put it back afterwards.
  - On: the lock is strict for the app too. Sound profiles cannot be applied and reminders play without raising the volume until you unlock.
- The quick settings tile refuses to switch while locked.
- An ongoing notification shows that the sound is locked. It has no unlock action on purpose.
- The lock is kept by the notification listener or the accessibility service, so at least one of them must be enabled. It is restored after a reboot.
- On Android 13 and later, sideloaded apps may need **Allow restricted settings** (App info › ⋮) before the accessibility service can be switched on.

Permissions: **Do Not Disturb access** for silent mode and Do Not Disturb, **Modify system settings** for vibrate for calls and touch vibration. Some phones do not let apps change these two vibration settings at all.

## How it works

A `NotificationListenerService` watches the notifications of the selected apps. Missed calls are the non-ongoing notifications of the default phone app (and of the system telecom service, or any notification with the `missed_call` category). SMS and MMS are the notifications of the default SMS app. While an app has at least one pending notification (not ongoing, not a foreground-service notification), the app keeps one reminder for it. An exact alarm wakes the app at each reminder time, and the reminder plays the configured melody and vibration. The reminder ends when the notification is opened or dismissed, when the total reminding time or the maximum number of reminders is reached, or when you stop it.

| Component | Role |
| --- | --- |
| `service/ReminderListenerService` | Detects posted and removed notifications |
| `engine/ReminderEngine` | Starts, advances and stops reminders |
| `model/Schedule` | Pure scheduling rules (active profile, profile switches, limits, snooze), unit tested |
| `engine/AlarmScheduler` | Exact alarms, optionally as alarm clocks to bypass Doze |
| `engine/AlertPlayer` | Plays sound and vibration for the alert duration |
| `engine/StatusNotifier` | Status notification with Stop and Snooze actions |
| `ui/*` | Jetpack Compose screens |

## Installing

1. Download the APK (from the GitHub Actions *Build* run artifacts, or build it yourself) and install it on the phone.
2. Open the app and grant the permissions shown in the **Permissions** card:
   - **Notification access** (required). On Android 13 and later, sideloaded apps may show *Restricted setting*. In that case open **App info**, tap **⋮**, choose **Allow restricted settings**, then grant notification access again.
   - **Show notifications** (for the status notification).
   - **Alarms and reminders** (exact timing).
   - **Unrestricted battery use** (recommended, otherwise the system can delay reminders).
3. Tap **Add apps**, choose the apps, then adjust **Default settings** or tap an app for its own settings.

If **Turn on screen** does nothing on Android 14 or later, check *Settings › Apps › Special app access › Turn screen on* and allow the app.

Some manufacturers (Xiaomi, Huawei, Samsung, OnePlus, etc.) add their own battery restrictions. If reminders stop after a while, allow the app to auto-start and exclude it from battery optimization in the manufacturer's settings, or enable **Maximum reliability** in the app.

## Building

Requirements: JDK 17 or later and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26). Target: Android 16 (API 36).

## Notes

The app requests `QUERY_ALL_PACKAGES` to list installed apps and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Both are fine for personal use and sideloading but would need a policy justification on Google Play.

## Licence

MIT License, Copyright (c) 2026 DIMITRIADIS George. Free to use, change and share, keeping the copyright notice. See [LICENSE](LICENSE).
