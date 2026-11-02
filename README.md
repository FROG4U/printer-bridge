# ResDineOS Printer Bridge (Android)

A tiny Android app whose **only job is the receipt printer**. It holds a rock-solid
connection to a Bluetooth (Classic) thermal printer and exposes a small local
endpoint on the tablet. ResDineOS (running in Chrome on the same tablet) hands it
the receipt, and the bridge prints it over its stable link.

This exists because **Web Bluetooth inside Chrome** (what ResDineOS uses today) can
only grab the printer briefly and keeps dropping. A native app can keep **Bluetooth
Classic (SPP)** connected permanently and auto-reconnect — which is far more reliable.

```
ResDineOS (Chrome, HTTPS)  ──POST /print (ESC/POS bytes)──▶  Printer Bridge app  ──Bluetooth Classic──▶  Printer
        same Android tablet                                   (http://127.0.0.1:9100)
```

## What it does
- Pick a **paired** Bluetooth printer in the app, tap **Start bridge**.
- Runs a foreground service that keeps the printer connected (auto-reconnects if it drops).
- Serves `http://127.0.0.1:9100` on the tablet:
  - `GET  /status` → `{ "connected": true, "printer": "XP-58" }`
  - `POST /print`  → body = raw ESC/POS bytes → prints them, returns `{ "ok": true }`
  - CORS + Private-Network-Access headers so the ResDineOS HTTPS page can reach it.

## Building the APK (no Android tools on your Mac needed)
This repo has a **GitHub Actions** workflow (`.github/workflows/build-apk.yml`) that
compiles a debug APK in the cloud on every push. Steps:
1. Push this folder to a GitHub repo (e.g. `FROG4U/printer-bridge`).
2. Open the repo → **Actions** → the latest run → download the **app-debug.apk** artifact.
3. Sideload that APK onto the tablet (copy it over / open it, allow "install from this source").

(You can also open the folder in **Android Studio** and press Run.)

## Installing on a tablet
1. In the tablet's Bluetooth settings, **pair** the printer once.
2. Install `app-debug.apk`.
3. Open "ResDineOS Printer Bridge", pick the printer, tap **Start bridge**, allow the
   permissions + notification. Leave it running (it survives in the background).
4. Tap **Test print** to confirm a slip comes out.

## Connecting ResDineOS to it
On the tablet, ResDineOS is set (for that restaurant) to **Bridge** print mode and
sends receipts to `http://127.0.0.1:9100/print` instead of Web Bluetooth. The web side
is enabled per-restaurant (starting with your development restaurant only).

## Notes
- Chrome treats `http://127.0.0.1` as a secure origin, so the HTTPS page can call it.
- The printer must support **Bluetooth Classic / SPP** (nearly all ESC/POS receipt
  printers do). BLE-only printers won't work with this bridge.
- Tablet only — iPads can't run this (same as Web Bluetooth).
