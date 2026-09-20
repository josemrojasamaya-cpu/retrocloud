#!/usr/bin/env sh
# Uso: run-emulator.sh gba|ds RUTA_RELATIVA_DEL_ARCHIVO
set -eu
platform="${1:?plataforma requerida}"; game="${2:?archivo privado requerido}"
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
case "$game" in *..*|/*) echo "Ruta de juego no válida" >&2; exit 2;; esac
case "$platform" in
  gba) image="retrosala/mgba:0.10.5" ;;
  ds) image="retrosala/melonds:1.1" ;;
  *) echo "Plataforma admitida: gba o ds" >&2; exit 2 ;;
esac

# Requiere una sesión gráfica virtual y contenido que el dueño haya colocado
# manualmente en las rutas privadas. Este script no descarga ningún archivo.
exec docker run --rm --network none \
  -v "$root/games-private:/private/games:ro" \
  -v "$root/bios-private:/private/bios:ro" \
  -v "$root/saves-private:/private/saves" \
  "$image" "/private/games/$game"
