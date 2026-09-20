# RetroSala

Cliente Android ligero para proyectores Android TV/Google TV. Recibe audio y vídeo de una sesión de emulación remota y reenvía los controles recibidos desde celulares conectados mediante QR.

Esta versión contiene una demostración local de la interfaz: usa un catálogo ficticio y una sesión remota simulada. No contiene, descarga, almacena ni ejecuta ROMs, BIOS, partidas o emuladores en el proyector.

## Arquitectura actual

- `app`: APK para proyector, catálogo, reproducción de streaming futura y puente del mando web.
- `catalog`: contrato para consultar el catálogo remoto por `gameId`, plataforma, idioma, servidor de emulación, disponibilidad de streaming y jugadores.
- `emulation`: contrato de sesiones remotas, vídeo, audio, controles, pausas, guardado, errores y desconexiones.
- `controller`: contrato del servidor WebSocket local que empareja celulares por QR. Por ahora se simula en pantalla.

Consulta [ARQUITECTURA.md](ARQUITECTURA.md) para los límites entre servicios.

## Compilar

Se necesita JDK 17, Android SDK 35 y Gradle 8.9 o posterior. Desde la raíz:

```powershell
gradle :app:assembleDebug
```

La APK, si la compilación termina, queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Probar la demostración

1. Instala la APK en un proyector Android compatible.
2. Abre RetroSala y elige un juego ficticio.
3. Pulsa **Iniciar demostración** para simular la sesión remota.
4. El QR representa el futuro enlace del mando. La pantalla muestra la conexión y los controles simulados.

## Limitaciones de esta etapa

El QR no abre todavía un servidor WebSocket real; el catálogo es local y ficticio; no hay autenticación, API ni WebRTC. La siguiente etapa debe implementar esos servicios con GBA y Nintendo DS en un servidor remoto autorizado.

