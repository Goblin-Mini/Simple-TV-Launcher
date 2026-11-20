# Simple TV Launcher

[English](README.md) | **Русский**

Лёгкий лаунчер для Android TV в стиле tvOS: «матовое стекло», плитки приложений, погода, быстрые настройки. Без AndroidX, около 150 КБ.

<img width="1920" height="1080" alt="photo_1_2026-11-20_23-49-09" src="https://github.com/user-attachments/assets/bdc50171-cd1f-4d2b-9eb2-0d3adc442961" />
<img width="1920" height="1080" alt="photo_2_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/55f39c73-cec2-42e0-9300-83f936d00d95" />
<details>

<summary>More screenshots</summary>

<img width="1920" height="1080" alt="photo_3_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/dd6abf04-b92a-4f22-80e2-b3933fec77cb" />
<img width="1920" height="1080" alt="photo_4_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/cbd59b69-8d66-411f-a8ce-77eab5dc9fac" />
<img width="1920" height="1080" alt="photo_5_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/b92c3e6d-cc80-463a-970e-f8d062f7575a" />
<img width="1920" height="1080" alt="photo_6_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/1f921875-2e83-44a5-94fa-093b2381037d" />
<img width="1920" height="1080" alt="photo_7_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/10f5dbf4-75b2-475e-ae87-c7f4a16534d4" />
<img width="1920" height="1080" alt="photo_8_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/62825014-bfab-4925-b096-0b91cf0dc675" />
<img width="1920" height="1080" alt="photo_9_2026-11-20_23-49-10" src="https://github.com/user-attachments/assets/ec2aaceb-6579-4fc5-a386-59ba31de55d1" />
</details>

**Скачать:** [последняя версия в Releases](https://github.com/J0NGLES/Simple-TV-Launcher/releases/latest) · **Обсуждение:** [4PDA](https://4pda.to/forum/index.php?showtopic=1129772)

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
