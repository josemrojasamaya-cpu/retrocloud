FROM ubuntu:24.04 AS builder
ARG MELONDS_REF=1.1
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential ca-certificates cmake extra-cmake-modules git libarchive-dev libcurl4-gnutls-dev \
    libenet-dev libfaad-dev libpcap0.8-dev libsdl2-dev libzstd-dev qt6-base-dev qt6-base-private-dev \
    qt6-multimedia-dev qt6-svg-dev \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch ${MELONDS_REF} https://github.com/melonDS-emu/melonDS.git /src/melonDS
RUN cmake -S /src/melonDS -B /src/melonDS/build && cmake --build /src/melonDS/build -j"$(nproc)"

FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends \
    libarchive13 libcurl3t64 libenet7 libfaad2 libpcap0.8-0 libsdl2-2.0-0 libzstd1 \
    qt6-base-dev-tools qt6-multimedia-dev-tools qt6-svg-dev-tools xvfb \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /src/melonDS/build/melonDS /usr/local/bin/melonDS
ENTRYPOINT ["/usr/local/bin/melonDS"]
