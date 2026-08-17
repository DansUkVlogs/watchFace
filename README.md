# ClinicalWatch

A minimal Wear OS 6 clock app designed for manual clinical timing on a Galaxy Watch4.

## What it does

- Looks like a clean analogue watch face while running as an app.
- Smooth red seconds hand while active.
- Seconds hand remains present in ambient mode and requests one-second redraws.
- Clear 15 / 30 / 45 / 60 observation markers.
- Date and battery while active.
- OLED-black background and simplified ambient presentation.
- No network permission, accounts, analytics, or data collection.

> This is a timing display, not a medical measurement or diagnostic device.

## Download the APK

Open **Actions** in this repository, select the latest successful **Build Wear OS APK** run, then download the `ClinicalWatch-debug-apk` artifact. Unzip it to get `app-debug.apk`.

## Install on a Galaxy Watch

Enable Developer options and Wireless debugging on the watch, then use Android Debug Bridge (ADB) from a computer to install the APK. Android Studio's Device Manager can also pair to the watch over Wi-Fi and install/run the app.

## Important test

Wear OS 6 / Android 16 changes ambient behaviour. The app targets API 36 and is deliberately implemented as an activity rather than a Watch Face Format face so it can use app ambient behaviour. Please verify on the physical Galaxy Watch4 that the seconds hand continues updating after the display dims. Samsung firmware can still apply device-specific power behaviour.
