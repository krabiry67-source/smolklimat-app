# APK СмолКлимат

Android-приложение = WebView с прототипом (`app/src/main/assets/index.html`) + нативная установка обновлений (`SmolApp.installApk`).

## Собрать APK через GitHub (без Android Studio)
1. Создайте публичный репозиторий, загрузите в корень папки `android/` и `.github/`.
2. Вкладка **Actions** → «Build APK» → **Run workflow**. Через ~5 минут APK появится в артефактах сборки.
3. Для релиза: создайте тег `v1.0.0` (Releases → New release) — APK прикрепится к релизу автоматически. Кнопка «Обновить» в приложении находит его там.

## Подпись (обязательно до раздачи клиентам)
Без своего ключа APK подписан отладочным, и обновления поверх могут не встать.
```
keytool -genkey -v -keystore release.jks -alias smolklimat -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks
```
GitHub → Settings → Secrets → Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Ключ храните в надёжном месте — без него обновлять приложение нельзя.

## Собрать на VPS (через Claude)
«Установи JDK 17 и Android SDK (cmdline-tools, platform 34, build-tools 34), Gradle 8.7, собери проект в папке android командой gradle assembleRelease и положи APK в /opt/smolklimat/public».

## Обновить интерфейс
После правок прототипа файл `index.html` пересобирается из проекта дизайна и кладётся в `app/src/main/assets/`.
