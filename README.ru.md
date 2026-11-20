# Simple TV Launcher

[English](README.md) | **Русский**

Лёгкий лаунчер для Android TV в стиле tvOS: «матовое стекло», плитки приложений, погода, быстрые настройки. Без AndroidX, около 150 КБ.

**Скачать:** [последняя версия в Releases](https://github.com/J0NGLES/Simple-TV-Launcher/releases/latest) · **Обсуждение:** [4PDA](https://4pda.to/forum/index.php?showtopic=1129772)

## Возможности
- Плитки приложений, сортировка, скрытие, настраиваемая сетка и обои (фото, видео)
- Погода (MET Norway, 7Timer, Bright Sky), единицы: метрические / США / Великобритания
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
