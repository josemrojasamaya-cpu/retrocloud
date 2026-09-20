# RetroSala

Cliente Android ligero para proyectores Android TV/Google TV. Recibe audio y vídeo de una sesión de emulación remota y reenvía los controles recibidos desde celulares conectados mediante QR.

Esta versión contiene una demostración local de la interfaz: usa un catálogo ficticio y una sesión remota simulada. No contiene, descarga, almacena ni ejecuta ROMs, BIOS, partidas o emuladores en el proyector.

## Arquitectura actual

- `app`: APK para proyector, catálogo, reproducción de streaming futura y puente del mando web.
- `catalog`: contrato para consultar el catálogo remoto por `gameId`, plataforma, idioma, servidor de emulación, disponibilidad de streaming y jugadores.
- `emulation`: contrato de sesiones remotas, vídeo, audio, controles, pausas, guardado, errores y desconexiones.
- `controller`: servidor WebSocket local que empareja celulares por QR. Funciona sólo dentro de la misma Wi-Fi del proyector; sus controles se reflejan en el modo demostración.

Consulta [ARQUITECTURA.md](ARQUITECTURA.md) para los límites entre servicios.

## Compilar

Se necesita JDK 17, Android SDK 35 y Gradle 8.9 o posterior. Desde la raíz:

```powershell
gradle :app:assembleDebug
```

La APK, si la compilación termina, queda en `app/build/outputs/apk/debug/app-debug.apk`.

La preparación de Windows, versiones verificadas, correcciones y pasos de instalación están en [docs/COMPILACION_WINDOWS.md](docs/COMPILACION_WINDOWS.md).

## Probar la demostración

1. Instala la APK en un proyector Android compatible.
2. Abre RetroSala y elige un juego ficticio.
3. Pulsa **Iniciar demostración** para simular la sesión remota.
4. Escanea el QR desde un celular en la misma Wi-Fi. El navegador abre el mando y la pantalla muestra cada control recibido.

## Limitaciones de esta etapa

El catálogo es local y ficticio; no hay autenticación, API ni WebRTC. La sesión de emulación también es simulada: no hay vídeo ni audio remoto todavía. La siguiente etapa debe implementar esos servicios con GBA y Nintendo DS en un servidor remoto autorizado.
