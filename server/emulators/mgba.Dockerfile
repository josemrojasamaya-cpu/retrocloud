FROM ubuntu:24.04 AS builder
ARG MGBA_REF=0.10.5
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential ca-certificates cmake git libavcodec-dev libavformat-dev libavutil-dev \
    libedit-dev libpng-dev libsdl2-dev libswscale-dev libzip-dev pkg-config zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch ${MGBA_REF} https://github.com/mgba-emu/mgba.git /src/mgba
RUN cmake -S /src/mgba -B /src/mgba/build -DBUILD_QT=OFF -DBUILD_SDL=ON \
    && cmake --build /src/mgba/build -j"$(nproc)" \
    && cmake --install /src/mgba/build --prefix /opt/mgba

FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends libsdl2-2.0-0 xvfb \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /opt/mgba /opt/mgba
ENV PATH="/opt/mgba/bin:${PATH}"
ENTRYPOINT ["/opt/mgba/bin/mgba"]
