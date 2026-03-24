FROM postgres:18.3-bookworm

RUN apt-get update \
  && apt-get install -y --no-install-recommends postgresql-18-cron \
  && rm -rf /var/lib/apt/lists/*
