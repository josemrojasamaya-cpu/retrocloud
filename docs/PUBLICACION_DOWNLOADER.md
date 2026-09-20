# Publicación para Downloader

## Publicación actual

- Repositorio: https://github.com/josemrojasamaya-cpu/retrocloud
- Release: https://github.com/josemrojasamaya-cpu/retrocloud/releases/tag/v0.1.0
- APK debug: https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.1.0/RetroSala-debug.apk
- SHA-256: `A49561934C08E11E56A5D8C1DC62EE1CA0B554AFA41CB4371CD112155CEB1451`

La APK adjunta es la compilación debug actual. No contiene ROMs, BIOS ni juegos.

## Requisito para Downloader

Al momento de publicar esta versión, el repositorio es privado. GitHub responde con HTTP 404 a una solicitud anónima del enlace directo. Downloader no puede autenticarse en GitHub, por lo que antes de usarlo se debe hacer accesible públicamente el repositorio o publicar la APK en un alojamiento HTTPS público.

## Pasos en Downloader

1. Abre Downloader en el proyector.
2. Escribe exactamente esta URL:

   ```text
   https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.1.0/RetroSala-debug.apk
   ```

3. Selecciona **Go** o **Ir**.
4. Cuando termine la descarga, selecciona **Install** o **Instalar**.
5. Comprueba el archivo descargado contra el SHA-256 indicado arriba cuando dispongas de una herramienta de verificación.

No se debe usar una ruta de trabajo local, `app/build/` ni un enlace a contenido privado de juegos.
