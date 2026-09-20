# Publicación para Downloader

## Publicación actual

- Repositorio: https://github.com/josemrojasamaya-cpu/retrocloud
- Release: https://github.com/josemrojasamaya-cpu/retrocloud/releases/tag/v0.2.0
- APK debug: https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.2.0/Gran-Z-Retro-v0.2.0-debug.apk
- SHA-256: `ADF27D3DC99A2D519F022B70C17BE0736E934BB2D9BC0859AB987C853EB125E9`

La APK adjunta es la compilación debug actual. No contiene ROMs, BIOS ni juegos.

El repositorio y la Release deben ser públicos para que Downloader pueda descargar la APK sin iniciar sesión.

## Pasos en Downloader

1. Abre Downloader en el proyector.
2. Escribe exactamente esta URL:

   ```text
   https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.2.0/Gran-Z-Retro-v0.2.0-debug.apk
   ```

3. Selecciona **Go** o **Ir**.
4. Cuando termine la descarga, selecciona **Install** o **Instalar**. Android normalmente permitirá actualizar la versión 0.1.0 sin desinstalarla si conserva la misma firma de depuración.
5. Comprueba el archivo descargado contra el SHA-256 indicado arriba cuando dispongas de una herramienta de verificación.

No se debe usar una ruta de trabajo local, `app/build/` ni un enlace a contenido privado de juegos.
