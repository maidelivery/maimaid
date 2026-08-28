FROM postgres:18.6-bookworm

ARG DEBIAN_MIRROR=mirrors.aliyun.com

RUN find /etc/apt -type f \( -name '*.list' -o -name '*.sources' \) \
    -exec sed -i "s|deb.debian.org|${DEBIAN_MIRROR}|g" {} + \
  && apt-get update \
  && apt-get install -y --no-install-recommends postgresql-18-cron \
  && rm -rf /var/lib/apt/lists/*
