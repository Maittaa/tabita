# Tabita

Личное Android-приложение: ежедневный чек-лист, плитки, графики, Health Connect, openScale, питание, финансы, самочувствие.

- Пакет `org.maittaa.tabita`, версия 5.17.2 (versionCode 26)
- Kotlin + Jetpack Compose, AGP 8.13, compileSdk 36, minSdk 28, JDK 21

## Сборка

Ключ подписи в репозиторий не входит. Положите `keystore/dnevnik.jks` и создайте в корне `keystore.properties`:

```
storeFile=keystore/dnevnik.jks
storePassword=...
keyAlias=dnevnik
keyPassword=...
```

Затем `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.
