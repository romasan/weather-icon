# Погода на иконке (Android 15+)

Приложение ищет город через Open-Meteo Geocoding API, показывает текущую температуру и погодные условия через WeatherAPI.com, сохраняет последний ответ и обновляет иконку приложения.

## WeatherAPI.com

Текущая погода загружается с `https://api.weatherapi.com/v1/current.json` по координатам выбранного города. Поиск города оставлен на Open-Meteo Geocoding API. Ключ не хранится в исходниках: задайте `weatherapi.key=ВАШ_КЛЮЧ` в локальном `local.properties` рядом с `settings.gradle.kts`. При сборке ключ передаётся в APK через `BuildConfig`.

## Обновления и значок

Иконки приложения переключаются через `activity-alias`: подготовлены статические варианты для температур от −39 до +39 градусов и шести типов условий: ясно, дождь, снег, смешанные осадки, гроза и туман. Температура вне этого диапазона ограничивается ближайшим значением на значке. Пока приложение открыто, погода запрашивается каждые 10 минут. Фоновый периодический запуск настроен на минимальные 15 минут WorkManager; Android может его задержать.

## Сборка

Требуются Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.11.1 и JDK 17. Инструменты разработчика находятся в `work/tools` рядом с проектом. Для отладки выполните `gradle assembleDebug`; для компактной release-сборки с R8 и удалением неиспользуемых ресурсов — `gradle assembleRelease`. Release APK создаётся неподписанным в `app/build/outputs/apk/release/app-release-unsigned.apk`; для установки или публикации его нужно подписать своим ключом.

## Ресурсы значка приложения

Шесть фоновых изображений лежат в `tools/weather-backgrounds`. Генератор хранит каждый фон один раз, создаёт 474 XML-значка с векторной температурой и обновляет `activity-alias` в манифесте. Это уменьшает размер набора иконок без конвертеров изображений. Нужен установленный JDK 17 или новее; укажите `JAVA_HOME` либо добавьте `java` и `javac` в `PATH`.

Windows PowerShell (если сценарии заблокированы, обход действует только для этого процесса):
```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
./tools/generate-icons.ps1
```

Windows CMD:
```bat
tools\generate-icons.cmd
```

macOS / Linux / Git Bash:
```bash
bash ./tools/generate-icons.sh
```

Чтобы изменить рисунок фона, замените соответствующий PNG в `tools/weather-backgrounds`. Размер каждого фона должен быть 128×128 пикселей. Диапазон можно изменить в константах `MIN_TEMPERATURE` и `MAX_TEMPERATURE` в `tools/GenerateWeatherIcons.java`; приложение при этом ограничивает отображаемую температуру в `MainActivity.kt`.
