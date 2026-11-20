# Simple TV Launcher

Лёгкий лаунчер для Android TV в стиле tvOS: «матовое стекло», плитки приложений, погода, быстрые настройки. Без AndroidX, около 150 КБ.

**Скачать:** [последняя версия в Releases](https://github.com/J0NGLES/Simple-TV-Launcher/releases/latest) · 
**Обсуждение:** [4PDA](https://4pda.to/forum/index.php?showtopic=1129772)

## Возможности
- Плитки приложений, сортировка, скрытие, настраиваемая сетка и обои (фото, видео)
- Погода (MET Norway, 7Timer, Bright Sky), единицы: метрические / США / Великобритания
- Индикатор сети с перечёркнутым значком, если нет доступа в интернет
- Голосовой поиск через выбранное приложение, выбор VPN-клиента
- Родительский контроль (PIN) и «Режим для бабушек»
- Резервная копия и восстановление настроек
- Русский и английский язык (по языку системы)

## Сборка
Нужны Android Studio (или JDK 17+ и Android SDK) и Gradle 9.x.

```
gradle assembleRelease
```

Без файла `keystore.properties` apk подписывается отладочным ключом. Для своей подписи создайте в корне проекта `keystore.properties`:

```
storeFile=путь/к/ключу.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Этот файл и `.jks` в `.gitignore` и не должны попадать в репозиторий.

Тестировалось на: RockTek G2, Mecool KM2 Deluxe, Xiaomi TV A Pro 2026, Philips 50PUS8506/60. minSdk 24.

## Лицензия
MIT, см. [LICENSE](LICENSE).

---

# Simple TV Launcher (English)

A lightweight tvOS-style Android TV launcher with a frosted-glass look: app tiles, weather, quick settings. No AndroidX, about 140 KB.

**Download:** [latest release](https://github.com/J0NGLES/Simple-TV-Launcher/releases/latest) · **Discussion:** [4PDA](https://4pda.to/forum/index.php?showtopic=1129772)

## Features
- App tiles with sorting, hiding, configurable grid and wallpapers (image, video)
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

Without `keystore.properties` the apk is signed with the debug key. To sign with your own key, create `keystore.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Never commit it or any `.jks` file.

Tested on RockTek G2, Mecool KM2 Deluxe, Xiaomi TV A Pro 2026, Philips 50PUS8506/60. minSdk 24.

## License
MIT, see [LICENSE](LICENSE).