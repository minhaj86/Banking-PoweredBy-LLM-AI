#!/usr/bin/env sh
# Shared by Compose health checks and read-only checks of older running containers.
set -eu
case "${1:-}" in
  storage) exec wget -T 5 -q -O /dev/null http://127.0.0.1:9333/cluster/status ;;
  mailpit) exec wget -T 5 -q -O /dev/null http://127.0.0.1:8025/readyz ;;
  *) exit 2 ;;
esac
