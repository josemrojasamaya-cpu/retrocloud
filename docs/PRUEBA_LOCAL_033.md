# Amayomi Retro 0.3.3: imagen, sonido y mando local

## Correcciones

- mGBA 0.10.5: renderer Qt nativo (`displayDriver=0` en `qt.ini`) para evitar capturas negras de OpenGL. Se conserva una copia `.amayomi-backup` antes del primer cambio. La ventana debe permanecer abierta, sin minimizar.
- FFmpeg captura el HWND del proceso creado, a 30 FPS y ancho 480; envía JPEG completos con cabeceras multipart. El lector Android tolera fragmentación y muestra errores/reconexión.
- Audio real de la salida predeterminada de Windows mediante WASAPI loopback, PCM s16le, y reproducción Android con AudioTrack. Se reinicia la captura si desaparece el dispositivo. Se desconectan clientes lentos para evitar acumulación ilimitada.
- Puente Python persistente: mensajes de teclado a la ventana del emulador, sin enviar teclas al resto del escritorio. A=X, B=Z, L=A, R=S, Start=Enter, Select=Backspace; joystick admite diagonales y liberación neutral.
- Rutas con espacios se resuelven mediante `fileURLToPath`; desaparece el fallo por `%20` en las rutas PowerShell.
- Una sola sesión de emulación simultánea. Directorio de guardados estable por `gameId`. Pausa/guardado remoto no implementados devuelven 501; controles no entregados devuelven 503.

La APK sigue siendo un cliente liviano. No incluye emuladores, juegos ni BIOS.

## Iniciar en esta PC

Desde la raíz del proyecto en PowerShell:

```powershell
& "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe" -m pip install -r .\server\requirements-audio.txt
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\scripts\start-local-pc.ps1 -BindAddress 192.168.100.33
```

El iniciador busca Python y FFmpeg; admite parámetros `-PythonExecutable`, `-FfmpegExecutable`, `-MgbaExecutable`, `-Port` y `-SessionToken`. Debe apuntarse al `.exe` real de FFmpeg, no a un `.bat`. En esta PC usa el FFmpeg 7.1 ya instalado por imageio-ffmpeg y Python 3.11. Audio añadido: PyAudioWPatch 0.2.12.8, licencia MIT, [fuente oficial](https://github.com/s0d3s/PyAudioWPatch).

La APK publicada está configurada para `http://192.168.100.33:8080` y el token local existente. API de controles y audio requieren Bearer token; `/v1/stream` conserva acceso LAN sin token por compatibilidad con APK anteriores. No abrir este puerto a Internet. Esta compilación de uso personal tiene configuración integrada; cambiar IP/token requiere recompilar hasta implementar ajustes en la app.

Colocar manualmente los juegos en `server/games-private/gba/` o `nds/` y registrarlos en `server/games-private/catalog.json`. BIOS y partidas se mantienen en `bios-private/` y `saves-private/`. Estas carpetas están ignoradas por Git.

## Prueba en el proyector

1. Actualizar a 0.3.3 conservando la instalación si Android permite actualizar con la misma firma.
2. PC, proyector y celular en la misma LAN; PC encendida y servidor iniciado.
3. Abrir la app y escanear el QR actual. Abrir un juego compatible con mGBA.
4. Esperar los indicadores `Video conectado` y `Audio conectado`. Start debe avanzar el juego; el joystick debe mover el menú.
5. Usar Salir para cerrar la sesión antes de abrir otro juego.

## Evidencia y comprobaciones del 20 de septiembre de 2026

- `node --test server/test/*.test.mjs`: 8 pruebas aprobadas (catálogo privado, archivo ausente, errores reales, multipart, memoria acotada, botones y joystick).
- `gradlew.bat assembleDebug test --no-daemon --console=plain` con parámetros `RETROSALA_SERVER_MODE=local_pc`, API local, streaming `/v1/stream` y token existente: BUILD SUCCESSFUL. Siete tests Android en cada variante debug/release.
- APK: aplicación `com.retrosala.app`, etiqueta Amayomi Retro, versión 0.3.3, versionCode 6, Android mínimo 8/API 26.
- HTTP MJPEG decodificado: imagen real del juego; Start cambió al menú y joystick abajo movió la selección. No fue una simulación ni solo una confirmación de recepción.
- PCM HTTP: 319284 bytes en la muestra inicial; RMS 2109, señal no silenciosa, 44100 Hz estéreo. Capturas y audio de prueba permanecen en `work/`, ignorado por Git.
- Archivo privado usado: Pokémon Crystal (Game Boy Color, compatible con mGBA; el catálogo actual lo agrupa en GBA). No equivale a validar todas las ROMs GBA o Nintendo DS.

## Límites actuales

No se ha observado la reproducción en el proyector físico ni medido su latencia. No hay un dispositivo ADB conectado. MJPEG y PCM son canales independientes, sin sincronización A/V por timestamps; no se presentan como WebRTC. Una sesión y un jugador efectivos; DS táctil y controles de melonDS no validados. No hay pausa ni save-state remoto todavía; usar el menú de guardado del propio juego. El cierre forzado del emulador no garantiza flush de un guardado pendiente.

WASAPI captura toda la salida predeterminada de Windows, incluidas otras aplicaciones, y también se sigue oyendo en la PC. No silenciar el mezclador de Windows: puede dejar el loopback sin sonido. Para evitar sonido físico duplicado, usar auriculares conectados a la salida capturada. Aislamiento por proceso y sincronización/compresión de audio son mejoras pendientes.

No se descargaron juegos, BIOS ni emuladores nuevos para esta corrección; se utilizaron el emulador y archivos privados existentes.
