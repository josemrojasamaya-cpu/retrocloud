#!/usr/bin/env sh
set -eu
docker build -f server/emulators/mgba.Dockerfile -t retrosala/mgba:0.10.5 .
docker build -f server/emulators/melonds.Dockerfile -t retrosala/melonds:1.1 .
