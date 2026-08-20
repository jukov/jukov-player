This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### iOS App Store release

The release target uses manual signing for Apple team `UDTBP44Q7F`, Bundle ID
`com.nberezovskii.jukovplayer`, and provisioning profile
`Jukovplayer App Store`. Signing assets stay on the publishing Mac and are
never committed.

One-time setup on the publishing Mac:

1. Create an Apple Distribution certificate from a CSR generated on that Mac,
   then install the returned `.cer`. The certificate must appear together with
   its private key under **My Certificates** in Keychain Access.
2. Install the `Jukovplayer App Store` provisioning profile for the explicit
   App ID `com.nberezovskii.jukovplayer`. The profile must contain the installed
   distribution certificate. A team Account Holder or Admin creates and
   refreshes this profile in Certificates, Identifiers & Profiles.
3. Sign in to Xcode with an App Store Connect user who can upload builds.
4. Keep the Firebase iOS app and `iosApp/iosApp/GoogleService-Info.plist`
   registered to the same Bundle ID so Crashlytics can upload release symbols.

Export a signed package without uploading it:

```shell
./scripts/publish-ios.sh 1 1.0
```

After validation, upload a new build to App Store Connect/TestFlight:

```shell
./scripts/publish-ios.sh 2 1.0.1 --upload
```

Every upload must use a new build number. Generated archives and export output
are kept under the gitignored `.context/ios-release/` directory.

### Signed Android release

Generate a release key (keep this file and its passwords backed up):

```shell
mkdir -p release
keytool -genkeypair -v \
  -keystore release/jukov-player.jks \
  -alias jukov-player \
  -keyalg RSA -keysize 2048 -validity 10000
```

Copy `keystore.properties.example` to `keystore.properties`, then replace the
placeholder passwords. Both the properties file and keystores are ignored by
Git. The same settings can instead be supplied with these environment variables:
`JUKOV_RELEASE_STORE_FILE`, `JUKOV_RELEASE_STORE_PASSWORD`,
`JUKOV_RELEASE_KEY_ALIAS`, and `JUKOV_RELEASE_KEY_PASSWORD`.

Build and install the signed release on a connected device:

```shell
./gradlew :androidApp:assembleRelease
adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

Publish a minified release App Bundle, including R8 mapping data and available
native debug symbols, to Google Play Internal Testing:

1. Create a Google Play service account with access to the app and enable the
   Android Publisher API.
2. Save its JSON key as `play-service-account.json` in the project root (the
   file is ignored by Git), or set `GOOGLE_PLAY_SERVICE_ACCOUNT_FILE`. When the
   command runs from another Git worktree, it also looks for the ignored file in
   the repository's primary worktree.
3. Use a new version code for every upload:

```shell
./scripts/publish-internal.sh 2 1.0.1
```

The script also accepts the service-account JSON directly through
`ANDROID_PUBLISHER_CREDENTIALS`. Release signing still comes from
`keystore.properties` or the `JUKOV_RELEASE_*` environment variables described
above.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Android device smoke (API 28 and 36): `./scripts/check-android-device.sh`
- Minified release smoke (API 36): `./scripts/check-android-device.sh --release-api-36`
- Android host coverage: `./scripts/check-coverage.sh`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
