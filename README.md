# Simple TV Launcher

**English** | [Русский](README.ru.md)

A lightweight tvOS-style Android TV launcher with a frosted-glass look: app tiles, weather, quick settings. No AndroidX, about 150 KB.

**Download:** [latest release](https://github.com/J0NGLES/Simple-TV-Launcher/releases/latest) · **Discussion (Russian):** [4PDA](https://4pda.to/forum/index.php?showtopic=1129772)

## Features
- App tiles with sorting, hiding, a configurable grid and wallpapers (image, video)
- Weather (MET Norway, 7Timer, Bright Sky); metric / US / UK units
- Network indicator with a crossed-out icon when there is no internet access
- Voice search via a chosen app, VPN client picker
- Parental control (PIN) and "Granny mode"
- Settings backup and restore
- Russian and English UI (follows the system language)

## Build
Requires Android Studio (or JDK 17+ and the Android SDK) and Gradle 9.x.

```
gradle assembleRelease
```

Without `keystore.properties` the apk is signed with the debug key. To sign with your own key, create `keystore.properties` in the project root:

```
storeFile=path/to/key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Never commit this file or any `.jks` file (they are listed in `.gitignore`).

Tested on RockTek G2, Mecool KM2 Deluxe, Xiaomi TV A Pro 2026, Philips 50PUS8506/60. minSdk 24.

## License
MIT, see [LICENSE](LICENSE).
