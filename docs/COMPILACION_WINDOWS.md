# Compilar RetroSala en Windows

## Herramientas utilizadas

| Herramienta | Versión |
|---|---|
| Eclipse Temurin JDK | 17.0.20.101 Hotspot |
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Android SDK Platform | API 35 |
| Android SDK Build-Tools | 35.0.0 |

El SDK está en `C:\Users\josma\AppData\Local\Android\Sdk`. Las variables de usuario `JAVA_HOME`, `ANDROID_HOME` y `ANDROID_SDK_ROOT` están configuradas para posteriores terminales.

## Correcciones de compilación

- Se generó el Gradle Wrapper dentro del repositorio, con distribución Gradle 8.9.
- Se alinearon Java y Kotlin a JVM 17. Antes Java compilaba para 1.8 y Kotlin para 17.
- Se corrigió una colisión de nombre en el callback de conexión del servidor WebSocket del mando.
- Se limitó Gradle a 1 GB y dos procesos para ajustarse a una computadora con 6 GB de RAM.

La advertencia sobre SDK XML versión 4 procede de las herramientas de Android más recientes y no impidió generar la APK.

## Generar la APK

Abre PowerShell en la raíz del repositorio y ejecuta:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

La APK debug queda en:

`app\build\outputs\apk\debug\app-debug.apk`

## Instalar en un proyector Android

1. Copia la APK al proyector mediante USB, un gestor de archivos o ADB.
2. En la configuración del proyector, habilita la instalación desde esa fuente cuando Android lo solicite.
3. Abre la APK e instala **RetroSala**.
4. Conecta el proyector y el celular a la misma Wi-Fi.
5. Abre RetroSala, escanea el QR y usa el mando web de demostración.

Para ADB, con el proyector conectado y autorizado:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Esta APK es de depuración y está firmada automáticamente. Antes de distribuir una versión final hay que crear una clave de firma propia y una APK release.
