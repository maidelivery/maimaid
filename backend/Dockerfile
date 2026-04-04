FROM node:25.8.1-bookworm-slim AS builder

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH

RUN apt-get update \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

RUN npm install -g pnpm@10.20.0

WORKDIR /app

COPY package.json ./
RUN pnpm install --ignore-scripts

COPY prisma ./prisma
COPY src ./src
COPY scripts ./scripts
COPY tsconfig.json ./tsconfig.json
COPY tsconfig.build.json ./tsconfig.build.json
COPY prisma.config.ts ./prisma.config.ts

RUN pnpm prisma generate && pnpm build && pnpm prune --prod

FROM oven/bun:1.3.2-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/prisma.config.ts ./prisma.config.ts
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules

EXPOSE 8787

CMD ["sh", "-c", "bunx --bun prisma migrate deploy && bun dist/server.js"]
