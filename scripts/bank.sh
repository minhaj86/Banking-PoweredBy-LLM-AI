#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMMAND="${1:-help}"
if (($#)); then shift; fi
STACK=iam WORKFLOW=docker SUITE=unit ENV_FILE=.env PROJECT_NAME='' TIMEOUT=600 TAIL=100
BUILD=false FOLLOW=false FAILURES=0
SERVICES=() COMPOSE=() FILES=() EXPECTED=() TARGETS=()

die() { printf 'FAIL %s\n' "$1" >&2; exit "${2:-2}"; }
check() { printf '%-4s %s\n' "$1" "$2"; if [[ $1 == FAIL ]]; then FAILURES=$((FAILURES+1)); fi; }
run() { local code=0; "$@" || code=$?; ((code == 0)) || die "Subprocess failed (exit $code). No later step was run." "$code"; }
contains() { local needle=$1 item; shift; for item in "$@"; do [[ $item != "$needle" ]] || return 0; done; return 1; }
tool() { if command -v "$1" >/dev/null 2>&1; then check PASS "$1 is available"; else check FAIL "$1 is missing. $2"; return 1; fi; }
help_text() {
  cat <<'HELP'
Placeholder Bank developer commands (Bash 3.2+)
  bank.sh help | doctor | start | stop | restart | build | test | deps | status | logs | clean

  --stack iam|backend|dev     Existing Compose definitions; default iam.
  --workflow docker|native   doctor/build workflow; default docker.
  --service NAME             Repeat for selected known Compose services.
  --project-name NAME        Explicit isolated project; default Compose/.env identity.
  --env-file PATH            Compose environment file, relative to repository root.
  --build                    start: explicitly build images before starting.
  --timeout SECONDS          Readiness deadline, default 600.
  --suite unit|integration|frontend|e2e|all  test selection, default unit.
  --tail LINES --follow      logs controls, default 100 lines without following.

Examples (work from any current directory):
  ./scripts/bank.sh doctor
  ./scripts/bank.sh build
  ./scripts/bank.sh start
  ./scripts/bank.sh deps --service loans
  ./scripts/bank.sh logs --service loans --tail 50 --follow
  ./scripts/bank.sh build --workflow native
  ./scripts/bank.sh test --suite all

Docker workflows require Git, Docker Engine/Desktop and Compose with --wait/!reset support;
no host JDK, Maven or Node is required. First initialize config with sh iam/init.sh (OpenSSL).
Native build/test requires JDK 21 / Maven Wrapper and Node >=24 / npm as selected.
Integration tests also require Docker; e2e requires the ready IAM stack and Chromium.
deps checks container readiness and the Compose dependency graph; it performs no package audit.

build, start --build, native build/test and first start may download pinned images/dependencies.
Build omits tests; use test explicitly. e2e installs the pinned Playwright Chromium browser.
start/restart affect services; stop preserves containers and every volume. No image pull policy
upgrades, volume deletion, prune or database reset is performed. logs may follow until Ctrl+C.
clean removes only paths in scripts/clean-paths.txt; it keeps dependencies, .env, .iam and volumes.
Exit: 0 success; 1 stack not ready; 2 prerequisites/usage; subprocess failures keep their exit code.
HELP
}
while (($#)); do
  case "$1" in
    --stack|--workflow|--suite|--service|--project-name|--env-file|--timeout|--tail)
      (($# >= 2)) || die "Missing value after $1"
      case "$1" in
        --stack) STACK=$2 ;; --workflow) WORKFLOW=$2 ;; --suite) SUITE=$2 ;;
        --service) SERVICES+=("$2") ;; --project-name) PROJECT_NAME=$2 ;;
        --env-file) ENV_FILE=$2 ;; --timeout) TIMEOUT=$2 ;; --tail) TAIL=$2 ;;
      esac; shift 2 ;;
    --build) BUILD=true; shift ;; --follow) FOLLOW=true; shift ;;
    --help|-h) COMMAND=help; shift ;; *) die "Unknown option '$1'. Use help." ;;
  esac
done
contains "$COMMAND" help doctor start stop restart build test deps status logs clean || die 'Unknown command. Use help.'
contains "$STACK" iam backend dev || die 'stack must be iam, backend or dev.'
contains "$WORKFLOW" docker native || die 'workflow must be docker or native.'
contains "$SUITE" unit integration frontend e2e all || die 'Unknown test suite.'
[[ $TIMEOUT =~ ^[0-9]+$ ]] && ((TIMEOUT >= 1 && TIMEOUT <= 3600)) || die 'timeout must be 1..3600 seconds.'
[[ $TAIL =~ ^[0-9]+$ ]] && ((TAIL >= 1 && TAIL <= 10000)) || die 'tail must be 1..10000 lines.'
[[ -z $PROJECT_NAME || $PROJECT_NAME =~ ^[a-z0-9][a-z0-9_-]*$ ]] || die 'Invalid project name.'
cd -- "$ROOT"

check_engine() {
  if tool docker 'Install Docker and start its daemon.'; then
    local version
    if version=$(docker info --format '{{.ServerVersion}}' 2>/dev/null); then check PASS "Docker daemon $version"
    else check FAIL 'Docker daemon unavailable. Start Docker Desktop/Engine.'; fi
    if version=$(docker compose version --short 2>/dev/null); then check PASS "Compose $version"
    else check FAIL 'Install a current Docker Compose with --wait and !reset support.'; fi
  fi
}
check_native() {
  local java_required=$1 node_required=$2 docker_required=${3:-false} file version java_command=java
  tool git 'Install Git and open a new shell.' || true
  if $java_required; then
    for file in mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.properties; do
      if [[ -f $ROOT/$file ]]; then check PASS "Wrapper file $file"; else check FAIL "Missing $file; restore the shared Maven Wrapper."; fi
    done
    if [[ -n ${JAVA_HOME:-} ]]; then java_command="$JAVA_HOME/bin/java"; fi
    if version=$("$java_command" -version 2>&1) && [[ $version =~ version\ \"21[.\"] ]]; then check PASS 'Java 21 is selected'
    else check FAIL 'Select an installed JDK 21 with JAVA_HOME and PATH.'; fi
  fi
  if $node_required; then
    if tool node 'Install Node 24 LTS or newer.'; then
      version=$(node --version)
      if [[ $version =~ ^v([0-9]+) ]] && ((BASH_REMATCH[1] >= 24)); then check PASS "Node $version satisfies package engines"
      else check FAIL 'Node >=24 is required by loan-web/package.json.'; fi
    fi
    tool npm 'Install Node with npm.' || true
    [[ -f $ROOT/loan-web/package-lock.json ]] || check FAIL 'Restore loan-web/package-lock.json for npm ci.'
  fi
  if $docker_required; then check_engine; fi
}
compose_context() {
  FILES=("$ROOT/compose.yaml")
  if [[ $STACK != backend ]]; then FILES+=("$ROOT/compose.iam.yaml"); fi
  if [[ $STACK == dev ]]; then FILES+=("$ROOT/compose.dev.yaml"); fi
  case "$ENV_FILE" in /*|[A-Za-z]:*) ;; *) ENV_FILE="$ROOT/$ENV_FILE" ;; esac
  COMPOSE=(compose --project-directory "$ROOT" --env-file "$ENV_FILE")
  local file
  for file in "${FILES[@]}"; do COMPOSE+=(-f "$file"); done
  if [[ -n $PROJECT_NAME ]]; then COMPOSE+=(--project-name "$PROJECT_NAME"); fi
}
read_config() {
  local file name value model services
  if [[ -f $ENV_FILE ]]; then check PASS 'Compose environment file exists; values are never printed'
  else check FAIL 'Missing environment file. Run sh iam/init.sh (or the backend init-local.sh).'; fi
  for file in "${FILES[@]}"; do [[ -f $file ]] || check FAIL "Missing Compose file: $file"; done
  ((FAILURES == 0)) || return 0
  # Read names/literal values only. Never source, eval or export an environment file.
  local names
  names=$(sed 's/\$\$/@@/g' "${FILES[@]}" | grep -oE '\$\{[A-Z][A-Z0-9_]*(:\?[^}]*)?\}' | sed -E 's/^\$\{([A-Z][A-Z0-9_]*).*$/\1/' | sort -u) || true
  while IFS= read -r name; do
    [[ -n $name ]] || continue
    value=${!name:-}
    if [[ -z $value ]]; then
      value=$(awk -v key="$name" 'match($0,/^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=/) {k=substr($0,1,RLENGTH-1);gsub(/[[:space:]]/,"",k);if(k==key){v=substr($0,RLENGTH+1);sub(/^[[:space:]]+/,"",v);sub(/[[:space:]]+$/,"",v);print v;exit}}' "$ENV_FILE")
      if ((${#value} >= 2)); then
        if [[ $value == \"*\" || $value == \'*\' ]]; then value=${value:1:${#value}-2}; fi
      fi
    fi
    if [[ -z $value ]]; then check FAIL "Missing variable $name; initialize/supply it without replacing existing keys."; fi
  done <<< "$names"
  ((FAILURES == 0)) || return 0
  model=$(docker "${COMPOSE[@]}" config --format yaml 2>/dev/null) || { check FAIL 'Compose configuration is invalid; check syntax, variables and version. Expanded values are suppressed.'; return 0; }
  services=$(docker "${COMPOSE[@]}" config --services 2>/dev/null) || { check FAIL 'Cannot resolve expected Compose services.'; return 0; }
  while IFS= read -r name; do [[ -z $name ]] || EXPECTED+=("$name"); done <<< "$services"
  EDGES=$(printf '%s\n' "$model" | awk '
    /^services:$/ {inside=1;next} inside && /^[^ ]/ {inside=0}
    inside && /^  [A-Za-z0-9_-]+:$/ {consumer=$1;sub(/:$/,"",consumer);deps=0}
    inside && /^    depends_on:$/ {deps=1;next}
    inside && /^    [^ ]/ {deps=0}
    deps && /^      [A-Za-z0-9_-]+:$/ {dependency=$1;sub(/:$/,"",dependency)}
    deps && /^        condition:/ {print consumer "|" dependency "|" $2}')
  PORTS=$(printf '%s\n' "$model" | awk '$1=="published:" {gsub(/"/,"",$2);if($2+0>0) print $2}' | sort -u)
  local project
  project=$(printf '%s\n' "$model" | awk '/^name:/ {print $2;exit}')
  for name in "${SERVICES[@]}"; do contains "$name" "${EXPECTED[@]}" || die "Unknown service '$name'. Use a service from the selected Compose stack."; done
  check PASS "Compose project $project; ${#EXPECTED[@]} expected services ($STACK)"
}
add_dependencies() {
  local consumer=$1 source dependency condition
  while IFS='|' read -r source dependency condition; do
    if [[ $source == "$consumer" ]] && ! contains "$dependency" "${TARGETS[@]}"; then
      TARGETS+=("$dependency"); add_dependencies "$dependency"
    fi
  done <<< "$EDGES"
}
get_targets() {
  local dependencies=$1 name
  TARGETS=()
  if $dependencies; then
    if ((${#SERVICES[@]})); then TARGETS=("${SERVICES[@]}"); for name in "${SERVICES[@]}"; do add_dependencies "$name"; done
    else for name in "${EXPECTED[@]}"; do add_dependencies "$name"; done; fi
  elif ((${#SERVICES[@]})); then TARGETS=("${SERVICES[@]}")
  else TARGETS=("${EXPECTED[@]}"); fi
}
read_service() {
  local name=$1 ids id data state health exit_code complete=false binding
  ROW_STATE='' ROW_HEALTH='' ROW_ENDPOINT='' ROW_DETAIL='' ROW_READY=true
  ids=$(docker "${COMPOSE[@]}" ps -a --quiet "$name" 2>/dev/null) || die 'Cannot inspect containers; Docker may be unavailable.'
  if [[ -z $ids ]]; then ROW_STATE='not created'; ROW_HEALTH='not ready'; ROW_DETAIL='Run start'; ROW_ENDPOINT='-'; ROW_READY=false; return 0; fi
  if printf '%s\n' "$EDGES" | grep -qE "^[^|]+\|${name}\|service_completed_successfully$"; then complete=true; fi
  while IFS= read -r id; do
    data=$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{.State.ExitCode}}' "$id" 2>/dev/null) || die 'Docker inspection failed.'
    IFS='|' read -r state health exit_code <<< "$data"
    ROW_STATE="${ROW_STATE:+$ROW_STATE,}$state"
    if $complete && [[ $state == exited && $exit_code == 0 ]]; then health=completed
    elif [[ $state != running ]]; then health=stopped; ROW_READY=false; ROW_DETAIL="exit $exit_code"; [[ $exit_code == 0 ]] || health=failed
    elif [[ $health == none && ( $name == storage || $name == mailpit ) ]]; then
      if docker "${COMPOSE[@]}" exec -T "$name" sh -s -- "$name" < "$ROOT/scripts/container-health.sh" >/dev/null 2>&1; then health='healthy (live probe)'
      else health=unhealthy; ROW_READY=false; ROW_DETAIL='Live readiness probe failed'; fi
    elif [[ $health != healthy ]]; then ROW_READY=false; ROW_DETAIL='Health check pending/failed'; if [[ $health == none ]]; then health=unverified; ROW_DETAIL='Add a meaningful Compose/image healthcheck'; fi
    fi
    ROW_HEALTH="${ROW_HEALTH:+$ROW_HEALTH,}$health"
    while IFS= read -r binding; do [[ -z $binding ]] || ROW_ENDPOINT="${ROW_ENDPOINT:+$ROW_ENDPOINT,}http://${binding##* -> }"; done < <(docker port "$id" 2>/dev/null)
  done <<< "$ids"
  [[ -n $ROW_ENDPOINT ]] || ROW_ENDPOINT=internal
}
show_status() {
  local dependencies=${1:-false} name source dependency condition ready=true
  get_targets "$dependencies"
  if $dependencies; then
    while IFS='|' read -r source dependency condition; do
      if [[ -n $dependency ]] && contains "$dependency" "${TARGETS[@]}"; then
        if ((${#SERVICES[@]} == 0)) || contains "$source" "${TARGETS[@]}"; then printf '  %s -> %s [%s]\n' "$source" "$dependency" "$condition"; fi
      fi
    done <<< "$EDGES"
  fi
  printf '%-20s %-14s %-24s %-30s %s\n' SERVICE STATE READINESS ENDPOINT DETAIL
  for name in "${TARGETS[@]}"; do
    read_service "$name"
    printf '%-20s %-14s %-24s %-30s %s\n' "$name" "$ROW_STATE" "$ROW_HEALTH" "$ROW_ENDPOINT" "$ROW_DETAIL"
    if ! $ROW_READY; then ready=false; fi
  done
  if $ready; then check PASS 'All selected services/dependencies are ready.'; return 0
  else check FAIL 'Expected services are not ready; use logs --service NAME.'; return 1; fi
}
check_ports() {
  local owned='' ids id port
  ids=$(docker "${COMPOSE[@]}" ps --quiet 2>/dev/null) || return 0
  while IFS= read -r id; do [[ -z $id ]] || owned="$owned $(docker port "$id" 2>/dev/null | sed -nE 's/.*:([0-9]+)$/\1/p' | tr '\n' ' ')"; done <<< "$ids"
  while IFS= read -r port; do
    [[ -n $port ]] || continue
    case " $owned " in *" $port "*) check PASS "Port $port belongs to this running Compose project" ;;
      *) if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then check FAIL "Port $port is occupied outside this project. Resolve the conflict before start."
         else check PASS "Loopback port $port is available"; fi ;;
    esac
  done <<< "$PORTS"
}
require_docker() {
  tool git 'Install Git.' || true
  check_engine
  ((FAILURES == 0)) || die 'Docker prerequisites failed; no services were changed.'
  compose_context; read_config
  ((FAILURES == 0)) || die 'Configuration checks failed; no services were changed.'
  if [[ ${1:-false} == true ]]; then check_ports; ((FAILURES == 0)) || die 'Port checks failed; no services were changed.'; fi
}
maven() { run sh "$ROOT/mvnw" -B -ntp "$@"; }
web() { (cd -- "$ROOT/loan-web" && run npm "$@"); }
clean_generated() {
  local relative target part path
  while IFS= read -r relative; do
    [[ -n $relative && $relative != \#* ]] || continue
    [[ $relative != /* && $relative != *..* ]] || die 'Invalid cleanup allowlist path.'
    target="$ROOT/$relative"
    [[ $target == "$ROOT/"* ]] || die 'Cleanup path leaves the repository.'
    path=$ROOT
    local components=()
    IFS='/' read -r -a components <<< "$relative"
    for part in "${components[@]}"; do path="$path/$part"; [[ ! -L $path ]] || die "Cleanup refuses a link/junction: $relative"; done
    if [[ -e $target ]]; then run rm -rf -- "$target"; printf 'Removed generated %s\n' "$relative"; fi
  done < "$ROOT/scripts/clean-paths.txt"
  check PASS 'Generated output cleaned; .env, .iam, source, dependencies and volumes preserved.'
}

case "$COMMAND" in
  help) help_text ;;
  clean) clean_generated ;;
  doctor)
    if [[ $WORKFLOW == native ]]; then check_native true true; else require_docker true; fi
    ((FAILURES == 0)) || die 'Prerequisites failed.' ;;
  build)
    ((${#SERVICES[@]} == 0)) || die 'build builds the complete configured project; use service for lifecycle commands.'
    if [[ $WORKFLOW == native ]]; then
      check_native true true; ((FAILURES == 0)) || die 'Native build prerequisites failed.'
      printf '%s\n' 'Building Java reactor and frontend; tests are omitted. Use test to run them.'
      maven -DskipTests package; web ci --no-fund; web run build
    else require_docker; printf '%s\n' 'Building configured images; Dockerfiles omit tests. Use test separately.'; run docker "${COMPOSE[@]}" build; fi ;;
  test)
    java_required=false node_required=false docker_required=false
    contains "$SUITE" unit integration all && java_required=true
    contains "$SUITE" frontend e2e all && node_required=true
    contains "$SUITE" integration all && docker_required=true
    check_native "$java_required" "$node_required" "$docker_required"
    ((FAILURES == 0)) || die 'Selected test prerequisites failed; tests were not executed.'
    if contains "$SUITE" e2e all; then
      [[ $STACK != backend ]] || die 'Browser tests require stack iam or dev.'
      require_docker; show_status || die 'Start the IAM stack before browser tests.' 1
    fi
    if [[ $SUITE == unit ]]; then maven test; fi
    if contains "$SUITE" integration all; then maven verify; fi
    if $node_required; then web ci --no-fund; fi
    if contains "$SUITE" frontend all; then web run typecheck; web run lint; web test; web run build; fi
    if contains "$SUITE" e2e all; then (cd -- "$ROOT/loan-web"; run npx --no-install playwright install chromium; run npm run test:e2e); fi
    check PASS "Executed test selection '$SUITE' completed successfully. Other suites were not run unless included by this selection." ;;
  *)
    ports=false; [[ $COMMAND != start ]] || ports=true
    require_docker "$ports"
    case "$COMMAND" in
      status) show_status || exit 1 ;; deps) show_status true || exit 1 ;;
      logs) options=(logs --tail "$TAIL"); if $FOLLOW; then options+=(--follow); fi; run docker "${COMPOSE[@]}" "${options[@]}" "${SERVICES[@]}" ;;
      stop) run docker "${COMPOSE[@]}" stop --timeout 30 "${SERVICES[@]}"; check PASS 'Services stopped. All containers and volumes are preserved.' ;;
      start)
        options=(up -d --wait --wait-timeout "$TIMEOUT")
        if $BUILD; then options+=(--build); else options+=(--no-build); fi
        code=0; docker "${COMPOSE[@]}" "${options[@]}" "${SERVICES[@]}" || code=$?
        if ((code != 0)); then show_status || true; printf '%s\n' 'Startup failed. Run scripts/bank.sh logs --service NAME --tail 80 with the same stack/project-name/env-file options.' >&2; exit "$code"; fi
        show_status || exit 1 ;;
      restart)
        run docker "${COMPOSE[@]}" restart --timeout 30 "${SERVICES[@]}"
        run docker "${COMPOSE[@]}" up -d --no-recreate --no-build --wait --wait-timeout "$TIMEOUT" "${SERVICES[@]}"
        show_status || exit 1 ;;
    esac ;;
esac
