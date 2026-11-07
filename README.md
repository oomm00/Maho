# DropWatch (Android)

Save product links → auto-fetch image + price → get notified on price drops. Folders + categories to organise.

## Build
1. Open this folder in **Android Studio** (Ladybug or newer). Let Gradle sync (it creates the wrapper).
2. Run on a device/emulator, or **Build > Build APK(s)**. APK lands in `app/build/outputs/apk/debug/`.

## How it works
- `Scraper.kt`  – on-device fetch + parse (JSON-LD Product → OpenGraph/product meta → microdata).
- `PriceCheckWorker.kt` – WorkManager, every 6 h on network; notifies on any drop or when target price is hit.
- Share any product link from Chrome → DropWatch (share sheet).
- Folders (e.g. "Birthday gifts") + free-text categories, filter chips on home.

## Limits
Sites that render price only via JavaScript or block bots (Amazon often does) may return no price →
use "Set price" manually. Android may delay background checks under battery saver.
