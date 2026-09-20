# Servidor de emulación de Gran Z Retro

Esta carpeta prepara el lado Linux del sistema. Ejecuta juegos autorizados únicamente en el servidor; la APK nunca recibe archivos de juego, BIOS ni partidas.

## Directorios privados

| Ruta | Propósito | Estado en Git |
|---|---|---|
| `games-private/` | archivos de juegos que posea el dueño | ignorado |
| `bios-private/` | BIOS que el dueño haya extraído legalmente | ignorado |
| `saves-private/` | partidas persistentes | ignorado |
| `sessions/` | datos efímeros de sesiones | ignorado |

Cada directorio solo versiona su archivo `.gitignore`; no debe añadirse ningún contenido privado al repositorio.

## Biblioteca privada

La APK consulta `GET /v1/catalog` y recibe únicamente los metadatos públicos de la biblioteca. El servidor no devuelve nombres de archivo ni rutas privadas y sólo muestra entradas cuyo archivo exista en `games-private/`.

En el host Linux, copia `catalog.private.example.json` como `games-private/catalog.json`, ajusta los identificadores y coloca los archivos autorizados en subcarpetas privadas, por ejemplo `games-private/gba/` y `games-private/ds/`. `catalog.json` queda ignorado por Git junto con los archivos de juego.

Antes de habilitar una biblioteca para el proyector, el host debe contener los archivos privados autorizados y sus dependencias de emulación. La API confirma `available: true` sólo cuando el archivo privado existe; WebRTC y la reproducción siguen pendientes.

## Emuladores preparados

| Plataforma | Emulador y versión fijada | Licencia | Fuente oficial |
|---|---|---|---|
| Game Boy Advance | mGBA 0.10.5 | MPL-2.0 | https://github.com/mgba-emu/mgba |
| Nintendo DS | melonDS 1.1 | GPL-3.0 | https://github.com/melonDS-emu/melonDS |

Los Dockerfiles clonan exclusivamente esas fuentes oficiales durante la construcción. No incluyen juegos ni BIOS. mGBA dispone de un frontend SDL, y melonDS usa un frontend Qt; ambos se preparan para una pantalla virtual Linux que la futura captura enviará al cliente.

## Iniciar la API de sesiones

En Linux con Docker Engine y Docker Compose:

```sh
docker compose -f server/docker-compose.yml up --build session-api
```

Comprobación:

```sh
curl http://localhost:8080/health
```

La API acepta `POST /v1/sessions` con `{ "gameId": "..." }`. Lee exclusivamente `games-private/catalog.json`, verifica que el archivo privado exista y rechaza el inicio si falta. Los endpoints `controls`, `pause`, `save` y `close` mantienen el ciclo de la sesión, sin exponer rutas privadas al cliente.

La biblioteca disponible para Android se obtiene con:

```sh
curl http://localhost:8080/v1/catalog
```

Define `SESSION_API_TOKEN` en un archivo `server/.env` privado para exigir `Authorization: Bearer TOKEN` en toda ruta `/v1/`. La APK envía ese token sólo cuando se configura `RETROSALA_SESSION_TOKEN` durante la compilación. No uses esta opción con tráfico público hasta activar TLS.

Ejemplos:

```sh
curl -X POST http://localhost:8080/v1/sessions -H 'content-type: application/json' -d '{"gameId":"gba-demo"}'
curl -X POST http://localhost:8080/v1/sessions/SESSION_ID/controls -H 'content-type: application/json' -d '{"control":"A","pressed":true}'
```

Construir los emuladores en contenedores, sin iniciarlos ni proporcionar contenido, requiere Docker:

```sh
sh server/scripts/build-emulators.sh
```

Para una ejecución aislada de prueba en Linux, el script acepta una ruta relativa que ya exista en `games-private/`; no descarga ni busca juegos:

```sh
sh server/scripts/run-emulator.sh gba subcarpeta/archivo-autorizado.gba
```

## Captura y WebRTC

La API ya expone un plan de captura por sesión: pantalla virtual Xvfb/Wayland, audio PulseAudio virtual, FFmpeg para H.264 o VP8 y Opus. WebRTC aún **no está implementado**: faltan señalización autenticada, servidor TURN, un proceso de captura por emulador, codificación de medios, DataChannel para controles y el reproductor WebRTC en Android.

## Hardware inicial

Para pruebas GBA/DS de un jugador: Linux x86_64, 4 núcleos, 8 GB de RAM, 30 GB SSD y red Ethernet de 1 Gb/s. Para una sesión con vídeo H.264 por software, asigna 2 vCPU; con GPU se recomienda codificación NVENC, VAAPI o Quick Sync. Nintendo DS puede requerir más CPU para renderizado 3D.

No se recomienda exponer esta API a Internet ni usarla como servicio público hasta implementar autenticación, TLS, límites de recursos y TURN.

## Prueba local en Windows

El modo `local-pc` permite que la APK se conecte solamente a esta computadora dentro de la misma red Wi-Fi. No publica juegos, BIOS ni rutas de Windows; usa un token obligatorio en cada llamada.

1. Coloca un archivo autorizado en `games-private/gba/` o `games-private/nds/`.
2. Copia `catalog.private.example.json` a `games-private/catalog.json` y cambia cada `file` por su ruta relativa privada. El catálogo y los archivos están ignorados por Git.
3. Obtén la IPv4 privada de esta computadora con `ipconfig`, por ejemplo `192.168.1.50`.
4. Inicia el modo local, reemplazando el token por uno largo y privado:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\server\scripts\start-local-pc.ps1 -BindAddress 192.168.1.50 -SessionToken "CAMBIA-ESTE-TOKEN-LARGO" -MgbaExecutable "C:\Program Files\mGBA\mGBA.exe" -MelondsExecutable "C:\ruta\a\melonDS.exe"
   ```

5. Compila la APK con `RETROSALA_SERVER_MODE=local_pc`, `RETROSALA_API_URL=http://192.168.1.50:8080` y el mismo `RETROSALA_SESSION_TOKEN`. La app sólo verá juegos cuyo archivo privado exista.

El ejecutable de mGBA se inicia desde `MGBA_EXECUTABLE` y melonDS desde `MELONDS_EXECUTABLE`; ambos permanecen en Windows. El proceso se considera `live` sólo después de abrirse sin salir de inmediato. Las acciones de controles, pausa y guardado se registran con seguridad, pero todavía necesitan un puente de automatización oficialmente compatible con esos frontends para llegar al proceso real. La captura disponible es una prueba técnica con FFmpeg/GDI (`capture-windows-session.ps1`); WebRTC, señalización y reproducción de audio/video en Android siguen pendientes. Por ello aún no se debe afirmar que un juego se pueda jugar desde el proyector.
