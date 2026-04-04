FROM node:25.8.1-bookworm-slim

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
COPY tsconfig.json ./tsconfig.json
COPY tsconfig.build.json ./tsconfig.build.json
COPY prisma.config.ts ./prisma.config.ts

RUN pnpm prisma generate && pnpm build

EXPOSE 8787

CMD ["sh", "-c", "pnpm prisma migrate deploy && node dist/server.js"]
