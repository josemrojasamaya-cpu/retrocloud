# Gran Z Retro

Gran Z Retro es el nombre visible del cliente Android para proyectores Android TV/Google TV. Está creado y fundado por José Amaya. El repositorio técnico mantiene el nombre RetroSala.

La aplicación recibe audio y vídeo de una sesión de emulación remota y reenvía los controles recibidos desde celulares conectados mediante QR.

Esta versión contiene una demostración local de la interfaz: usa un catálogo ficticio y una sesión remota simulada. No contiene, descarga, almacena ni ejecuta ROMs, BIOS, partidas o emuladores en el proyector.

## Arquitectura actual

- `app`: APK para proyector con identidad Gran Z Retro, catálogo por plataforma, reproducción de streaming futura y puente del mando web.
- `catalog`: contrato para consultar `gameId`, `platform`, `title`, `language`, `players`, `available` y `coverUrl` opcional.
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
2. Abre Gran Z Retro y elige Game Boy Advance o Nintendo DS.
3. Selecciona un juego disponible y pulsa **Iniciar sesión** para simular la sesión remota.
4. Escanea el QR desde un celular en la misma Wi-Fi. El navegador abre un mando horizontal con joystick, botones, pausa, salida y panel táctil para DS.

## Limitaciones de esta etapa

El modo demostración usa una biblioteca de muestra. Cuando se configura la API remota, Gran Z Retro carga únicamente las entradas disponibles de `GET /v1/catalog`, agrupadas por GBA y Nintendo DS. La sesión de emulación sigue siendo simulada: no hay vídeo ni audio remoto todavía. La siguiente etapa debe implementar esos servicios con GBA y Nintendo DS en un servidor remoto autorizado.
