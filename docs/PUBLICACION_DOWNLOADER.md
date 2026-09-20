# Publicación para Downloader

## Publicación actual

- Repositorio: https://github.com/josemrojasamaya-cpu/retrocloud
- Release: https://github.com/josemrojasamaya-cpu/retrocloud/releases/tag/v0.3.3
- APK debug: https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.3.3/Amayomi-Retro-v0.3.3.apk
- SHA-256: `D1916B1659974DEDAC5EEEF86B03DBC841561B122EC58BC7C434309F285B3674`
- Tamaño: 9.866.101 bytes. Nombre visible: Amayomi Retro; versionCode 6.

La APK adjunta es la compilación debug actual. No contiene ROMs, BIOS ni juegos.

Descarga pública comprobada el 20 de septiembre de 2026 con curl sin autenticación: HTTP 200, una redirección, MIME `application/vnd.android.package-archive`. El SHA-256 del archivo descargado coincide con la compilación local. No reutilizar la URL temporal de `release-assets.githubusercontent.com`: caduca; usar el enlace estable de arriba.

## Pasos en Downloader

1. Abre Downloader en el proyector.
2. Escribe exactamente esta URL:

   ```text
   https://github.com/josemrojasamaya-cpu/retrocloud/releases/download/v0.3.3/Amayomi-Retro-v0.3.3.apk
   ```

3. Selecciona **Go** o **Ir**.
4. Cuando termine la descarga, selecciona **Install** o **Instalar**. Android normalmente permitirá actualizar sin desinstalar si conserva la misma firma de depuración. Mantén los datos de la aplicación; comprueba que indique versión 0.3.3.
5. Comprueba el archivo descargado contra el SHA-256 indicado arriba cuando dispongas de una herramienta de verificación.

No se debe usar una ruta de trabajo local, `app/build/` ni un enlace a contenido privado de juegos.

Los códigos numéricos anteriores no se actualizan automáticamente al publicar otra Release. No se ha registrado ni verificado un código nuevo para 0.3.3; usar esta URL directa. Si se genera un código en `https://go.aftvnews.com/`, registrar exactamente el enlace estable de la APK y completar personalmente cualquier CAPTCHA solicitado.

La actualización incorpora reproducción de audio, video con reconexión y correcciones del mando hacia mGBA. Requiere el servidor Windows actualizado en `192.168.100.33:8080`, PC encendida y todos los dispositivos en la misma LAN. El servidor se comprobó con un juego privado compatible con mGBA; la reproducción final en el proyector y Nintendo DS no están confirmadas. Consulta [pruebas y límites](PRUEBA_LOCAL_033.md).
