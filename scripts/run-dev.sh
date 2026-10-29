#!/usr/bin/env bash
# Convenience entry point for the local Docker development stack.
# The canonical implementation lives in scripts/bank.sh; keeping this wrapper
# thin prevents the service/dependency graph from drifting between commands.
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
BANK="$ROOT/scripts/bank.sh"

usage() {
  cat <<'EOF'
Usage: scripts/run-dev.sh [command] [options]

Commands
  start      Start the local stack and wait for all services to be ready (default)
  stop       Stop services while preserving containers and volumes
  restart    Restart services and wait for readiness
  status     Show service state, readiness, endpoints, and failures
  deps       Show the dependency graph and readiness of dependencies
  logs       Show service logs (use --service NAME and --follow as needed)
  build      Build the configured Docker images
  doctor     Check Docker, Compose, configuration, and port prerequisites

Options are passed to scripts/bank.sh:
  --stack iam|backend|dev
  --service NAME       Select a service; repeat for multiple services
  --project-name NAME  Use an explicit Compose project name
  --env-file PATH      Use an environment file relative to the repository root
  --build              Build images before start
  --timeout SECONDS    Readiness deadline (default 600)
  --tail LINES         Number of log lines (default 100)
  --follow             Follow logs
  -h, --help           Show this help

Examples
  scripts/run-dev.sh start --build
  scripts/run-dev.sh status
  scripts/run-dev.sh deps --service loans
  scripts/run-dev.sh logs --service ledger --tail 80 --follow
  scripts/run-dev.sh stop

Stop preserves PostgreSQL, object storage, and all other Compose volumes.
EOF
}

COMMAND="${1:-start}"
if (($#)); then
  case "$1" in
    start|stop|restart|status|deps|logs|build|doctor) shift ;;
    help|-h|--help) usage; exit 0 ;;
  esac
fi

case "$COMMAND" in
  start|stop|restart|status|deps|logs|build|doctor)
    exec bash "$BANK" "$COMMAND" "$@"
    ;;
  *)
    printf 'Unknown command: %s\n\n' "$COMMAND" >&2
    usage >&2
    exit 2
    ;;
esac
