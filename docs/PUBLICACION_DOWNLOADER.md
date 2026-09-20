# Publicación para Downloader

La APK actual está lista como artefacto local de depuración, pero el repositorio todavía no tiene un remote GitHub configurado. Por eso no existe un enlace HTTPS final ni se puede crear una GitHub Release desde este proyecto.

Cuando se configure `origin` con el repositorio correcto y se publique un release, Downloader debe abrir el enlace directo HTTPS del archivo APK, con este formato:

```text
https://github.com/PROPIETARIO/RetroSala/releases/download/v0.1.0/RetroSala-debug.apk
```

Antes de distribuirla, crea una APK release firmada con una clave privada propia y publica su nombre final. El enlace de Downloader debe apuntar al asset de esa release, nunca a una ruta de trabajo, a `app/build/` ni a un archivo privado de juegos.
