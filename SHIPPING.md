# Shipping & Signing Guide for Maho

This guide explains how to generate a signing key, configure release signing, build production release artifacts (APK and AAB), and verify the signed outputs.

---

## 1. Generate a Release Keystore

Run the following `keytool` command in your terminal to create a production keystore file (`maho-release.jks`):

```bash
keytool -genkeypair -v \
  -keystore maho-release.jks \
  -alias maho_key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

> ⚠️ **CRITICAL WARNING:** Back up your `maho-release.jks` file and passwords securely (e.g. in a password manager). If you lose this keystore file, Google Play will **NOT** allow you to push updates to existing users.

---

## 2. Configure `keystore.properties`

1. Copy `keystore.properties.example` to `keystore.properties` in the project root:

   ```bash
   cp keystore.properties.example keystore.properties
   ```

2. Open `keystore.properties` and fill in your actual keystore details:

   ```properties
   storeFile=maho-release.jks
   storePassword=your_actual_store_password
   keyAlias=maho_key
   keyPassword=your_actual_key_password
   ```

> 🔒 Both `maho-release.jks` and `keystore.properties` are listed in `.gitignore` to prevent committing sensitive keys to git.

---

## 3. Build Production Artifacts

### Option A: Gradle Command Line
From the project root folder, run:

- **Build Signed APK:**
  ```bash
  ./gradlew assembleRelease
  ```

- **Build Signed App Bundle (AAB for Google Play):**
  ```bash
  ./gradlew bundleRelease
  ```

### Option B: Android Studio GUI
1. Open Android Studio.
2. Go to **Build** > **Generate Signed App Bundle / APK**.
3. Choose **Android App Bundle** or **APK**, click **Next**.
4. Select `maho-release.jks`, enter passwords, and select `release` build variant.
5. Click **Create**.

---

## 4. Output File Locations

- **Signed APK:** `app/build/outputs/apk/release/app-release.apk`
- **Signed AAB:** `app/build/outputs/bundle/release/app-release.aab`

---

## 5. Install Release APK on Device

Connect your device via USB or ADB wireless, then run:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 6. Verify Signature & Certificates

Verify that the APK is correctly signed and aligned using `apksigner`:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected output:
```
Signer #1 certificate DN: ...
Signer #1 certificate SHA-256 digest: ...
```

