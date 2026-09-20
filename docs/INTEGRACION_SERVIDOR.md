# Integración de la APK con el servidor de sesiones

RetroSala conserva el proyector como cliente liviano. La APK usa la API de sesiones para iniciar una partida y enviar controles; el servidor conserva juegos, BIOS, emuladores y partidas.

## Modos de compilación

| Modo | Uso | Requisitos de URL |
|---|---|---|
| `demo` | catálogo y sesión simulada actuales | ninguno |
| `local` | API en la red privada | `http://IP_DEL_SERVIDOR:8080` durante pruebas controladas |
| `remote` | servidor remoto futuro | API HTTPS y señalización WSS |

Las variables se entregan como propiedades de Gradle o variables de entorno durante la compilación. Nunca deben guardarse en Git si contienen tokens.

| Variable | Propósito |
|---|---|
| `RETROSALA_SERVER_MODE` | `demo`, `local` o `remote` |
| `RETROSALA_API_URL` | base de la API de sesiones |
| `RETROSALA_SIGNALING_URL` | señalización WebRTC futura |
| `RETROSALA_STREAMING_URL` | endpoint de streaming/WebRTC futuro |
| `RETROSALA_SESSION_TOKEN` | token Bearer de la API |

Ejemplo de compilación contra un servidor local en la LAN:

```powershell
$env:RETROSALA_SERVER_MODE = "local"
$env:RETROSALA_API_URL = "http://192.168.1.50:8080"
$env:RETROSALA_SESSION_TOKEN = "token-local-privado"
.\gradlew.bat assembleDebug
```

Ejemplo de producción futura:

```powershell
$env:RETROSALA_SERVER_MODE = "remote"
$env:RETROSALA_API_URL = "https://api.retrosala.example"
$env:RETROSALA_SIGNALING_URL = "wss://signal.retrosala.example"
$env:RETROSALA_STREAMING_URL = "https://stream.retrosala.example"
$env:RETROSALA_SESSION_TOKEN = "token-efimero"
.\gradlew.bat assembleDebug
```

Cuando hay API configurada, `ApiRemoteEmulationSession` llama `POST /v1/sessions`, conserva el identificador remoto y envía `controls`, `pause`, `save` y `close`. La sesión sigue sin decodificar vídeo, audio o ejecutar emuladores dentro de la APK.

## Preparación de WebRTC

La configuración ya reserva URL de señalización y streaming. La siguiente integración añade:

1. autenticación con token efímero emitido por el servidor;
2. señalización SDP/ICE por WSS;
3. TURN para redes fuera de la LAN;
4. reproductor de vídeo/audio WebRTC en Android;
5. DataChannel o WebSocket autenticado para controles;
6. límites por sesión y apagado garantizado del emulador.
