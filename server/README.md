# Servidor de emulación de RetroSala

Esta carpeta prepara el lado Linux del sistema. Ejecuta juegos autorizados únicamente en el servidor; la APK nunca recibe archivos de juego, BIOS ni partidas.

## Directorios privados

| Ruta | Propósito | Estado en Git |
|---|---|---|
| `games-private/` | archivos de juegos que posea el dueño | ignorado |
| `bios-private/` | BIOS que el dueño haya extraído legalmente | ignorado |
| `saves-private/` | partidas persistentes | ignorado |
| `sessions/` | datos efímeros de sesiones | ignorado |

Cada directorio solo versiona su archivo `.gitignore`; no debe añadirse ningún contenido privado al repositorio.

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
