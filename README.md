# Maho 📦

> *Maho saves the stuff you want to buy and yells when it gets cheaper. No ads, no accounts, no tracking. Your list stays on your phone.*

Maho is a minimal, image-first Android app built with modern Jetpack Compose, Material 3, Room, WorkManager, and OkHttp/Jsoup scrapers.

---

## 📲 Download

**→ [Download Maho v1.0 for Android](https://github.com/oomm00/Maho/raw/main/docs/download/Maho-v1.0.apk)** · 1.7 MB · Android 8.0+

Prefer a proper landing page? Visit **[oomm00.github.io/Maho](https://oomm00.github.io/Maho/)**.

The APK is a signed release build (`com.omsati.dropwatch`, versionCode 1). Android will ask you to allow installs from your browser or file manager the first time you sideload it.

SHA-256:

```text
f9694d80500e32948cf06a12b8a3cb9436efb46ec6c2e679a6b8368082bb0cc0
```

The landing page lives in [`docs/`](docs/) and is served directly from the `main` branch by GitHub Pages.

---

##  Features

- **Image-Led Folder Cards**: Automatic 2x2 photo collage covers generated from your saved items.
- **1:1 Smooth Collapsing Header**: Fluid 60/120 FPS top bar and search field that collapse smoothly on scroll down and reappear instantly on scroll up.
- **Price History & Charting**: Interactive Canvas line graph with range filters (`1M`, `3M`, `6M`, `1Y`, `All`), lowest/highest/average stats, and touch scrubbing tooltips.
- **Drag-to-Reorder**: Smooth reordering with scale, elevation, and haptic feedback via `sh.calvin.reorderable`.
- **Multi-Currency Support**: Per-product currency overrides, foreign currency detection warnings, and manual exchange rate conversion.
- **Bill Export**: Generate and share itemised plain-text receipts with per-currency subtotal breakdowns.
- **Background Price Checks**: Periodic WorkManager worker checking store pages for price drops with Android 13+ runtime notifications.
- **Editorial Palette**: Warm dark (`#0E0E0E`) & warm paper (`#F6F3EC`) theme with Space Grotesk & Inter typography.

---

## Tech Stack

- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: ViewModel, StateFlow, Coroutines, Clean Architecture
- **Local Database**: Room 2.6.1 with explicit migrations (`exportSchema = true`)
- **Background Tasks**: WorkManager 2.9.1
- **Networking & Parsing**: OkHttp 4.12.0, Jsoup 1.18.1, Hidden WebView Fallback
- **Image Loading**: Coil 2.7.0 with memory & disk caching
- **Reordering**: `sh.calvin.reorderable` 2.4.1

---

## Building & Shipping

1. Open the project in **Android Studio**.
2. Let Gradle sync dependencies.
3. Run on an emulator or physical device.

For signing and building release APK/AAB distribution packages, see **[`SHIPPING.md`](SHIPPING.md)**.

```bash
# Build signed release APK
./gradlew assembleRelease

# Build signed App Bundle for Google Play
./gradlew bundleRelease
```

---

## Author

Built by [@omsneakycow](https://instagram.com/omsneakycow).

- **Instagram**: [@omsneakycow](https://instagram.com/omsneakycow)
- **GitHub**: [oomm00](https://github.com/oomm00)
- **X**: [@Omsneakycow](https://x.com/Omsneakycow)

---

## 📄 License

[MIT License](LICENSE)
