FROM node:22-bookworm-slim
WORKDIR /srv/retrosala/server
COPY server/package.json ./package.json
COPY server/src ./src
EXPOSE 8080
CMD ["node", "src/index.mjs"]
