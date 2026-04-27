![BlueNox Library Banner](assets/bluenox-lib.png)

# BlueNox Android BLE Library

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/minSdk-24-blue.svg)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Language](https://img.shields.io/badge/language-Kotlin-orange.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-lightgrey.svg)](LICENSE)


BlueNox is a Kotlin-first Android BLE library focused on reliable scan/connect/GATT operations with structured failure handling, callback and Flow APIs, and optional beacon/DFU-oriented workflows.

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Permissions](#permissions)
- [Quick start](#quick-start)
- [Documentation map](#documentation-map)
- [Use cases](#use-cases)
- [Tags / topics](#tags--topics)
- [License](#license)

## Overview

We built the original Android BlueNox library back in 2015, when Android 4 Bluetooth Stack
had every kind of issue you could imagine. We've updated it in the years since to support
every version.

A few years ago we modernized the library to Kotlin and began shipping that version in application. We're releasing it because we want to make it easier for developers to write amazing BLE enabled applications.

## Features

- BLE scanning with optional filters (address, UUID, manufacturer ID, AD type).
- Device connection management and disconnection controls.
- GATT operations: read, write, notifications, MTU, connection priority, reliable write.
- Structured failure taxonomy via callback events for app-level retry/recovery logic.
- Kotlin `SharedFlow` adapter for coroutine-first event consumption.
- Beacon frame decoding helpers (iBeacon, Eddystone variants).
- Diagnostics and test-oriented mock BLE harness support.
- Broad Android Support - Android API 24+ Android 7.0 to Android 15

## Requirements

- Android API 24+ - Android 7.0 to Android 15
- Kotlin Android project
- BLE-capable device and runtime BLE permissions granted by user

## Installation

You can easily integrate the repo from the artifact repository:

```kotlin
dependencies {
    implementation("com.argenox:bluenox-android:<version>")
}
```

Example

```kotlin
dependencies {
    implementation("com.argenox:bluenox-android:0.2.52")
}
```

## Permissions

Runtime BLE permissions must be requested by the host app before initialization.

Recommended pattern:

1. Get required permissions from `BluenoxLEManager.getInstance().requiredPermissions()`.
2. Request them at runtime.
3. Initialize only after permissions are granted.

On newer Android versions, Bluetooth runtime permissions are required; location requirements vary by API level and scan behavior.

### Android Permissions: API Level, Scan Behavior, and Required Runtime Permissions

The permissions your app needs for BLE operations will depend on both the Android API level **and** how you are scanning for devices. Incorrect permissions may cause scanning/connection failures or deliver empty scan results.

#### Minimum Permissions by API Level

| API Level | Core Required Permissions                                              | Notes                                                   |
|-----------|----------------------------------------------------------------------|---------------------------------------------------------|
| 24–30     | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` \*             | Location required for most scans                        |
| 31+       | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (**new**), `ACCESS_FINE_LOCATION` \* | Bluetooth Scan/Connect mandatory starting API 31 (Android 12+) |

\* For background scanning and some beacon scans, you may also need `ACCESS_BACKGROUND_LOCATION`.

#### Scan Behavior and Location Permission

Starting with Android 6.0 (API 23), **location permission is required** for scanning and discovering BLE devices not explicitly marked as "owned" devices.  
- For most **foreground BLE scanning**, `ACCESS_FINE_LOCATION` is required (or `ACCESS_COARSE_LOCATION` on some old targeting).
- For **foreground scanning** on Android 12+ (API 31+), you must also request the new `BLUETOOTH_SCAN` permission with runtime consent.
- For **background scanning**, request `ACCESS_BACKGROUND_LOCATION` as well.

#### Example Permission Requests

- **AndroidManifest.xml**:
    ```xml
    <!-- Up to API 30 -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <!-- API 31+ -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <!-- Optional: -->
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    ```

- **Runtime Permission Prompt**:
    - Request permissions at runtime using `ActivityCompat.requestPermissions`, as **manifest-only declarations are not sufficient** on Android 6+.

#### Additional Notes

- If you only connect to paired/Bonded devices and never scan, location permissions may be avoided.  
- Background and foreground service permissions may also be needed for persistent long-running BLE connections/scans.
- Always check your target and minSdk versions, since Google Play requirements may enforce permission usage disclosure best practices.

See also:  
- [Android Bluetooth Permissions (official docs)](https://developer.android.com/guide/topics/connectivity/bluetooth/permissions)
- [Bluetooth and location](https://developer.android.com/guide/topics/connectivity/bluetooth/permissions#permissions-bluetooth-scan)

## Quick start

```kotlin
val manager = BluenoxLEManager.getInstance()

val initialized = manager.initialize(applicationContext)
if (!initialized) return

manager.scanForDevices(10_000L)
```

Then connect to a discovered device and perform read/write/notify through `BlueNoxDevice`.

## Documentation map

This README is intentionally concise. Use the companion docs for full details:

- `LIBRARY_USAGE_GUIDE.md` - end-to-end integration patterns, callback and Flow examples, lifecycle guidance.
- `API_REFERENCE.md` - manager/device APIs, callback contracts, failure taxonomy, event model.
- `LICENSE` - Apache 2.0 full license text.
- `NOTICE` - attribution and notice information.

## Use cases

- Medical, wearable, sensor, and industrial BLE apps.
- Production apps requiring deterministic error handling and telemetry.
- Apps migrating from callback-heavy code to coroutine/Flow event streams.
- Teams needing mock BLE scenario replay during development or testing.

## Tags / topics

`android` `ble` `bluetooth-low-energy` `kotlin` `gatt` `scan` `connect` `notifications` `mtu` `beacon` `eddystone` `ibeacon` `dfu`

## License

BlueNox is distributed under the Apache License 2.0.

- `LICENSE` - full Apache-2.0 terms
- `NOTICE` - attribution notice
