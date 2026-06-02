#!/usr/bin/env bash
# dev.sh - Start, stop, restart or rebuild the Webshop backend via Docker
# Usage:  ./dev.sh start   | stop | restart | rebuild
#         ./dev.sh stop    --keep-db   (stop backend container, keep PostgreSQL running)
#         ./dev.sh restart --keep-db
#         ./dev.sh rebuild --keep-db
#         ./dev.sh          (no argument: shows help and prompts interactively)
#
# Requirements: Docker Desktop (no Java or Maven needed locally)
#               python3 (optional - used for auto-rebuild detection)
#
# How it works:
#   start   -> docker compose up -d --build
#              Auto-detects source changes (src/, pom.xml, Dockerfile) and switches
#              to a full rebuild automatically if any file is newer than the last image.
#              (first run: Maven downloads ~200 MB of dependencies inside the container)
#              Polls /api/health until the app responds, then prints the ready message.
#   stop    -> docker compose down  (or stop only backend with --keep-db)
#   restart -> stop backend + start backend
#   rebuild -> docker compose down + remove postgres volume (fresh DB) + up --build --force-recreate
#              (use --keep-db to keep the volume and rebuild only the backend)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080
HEALTH_URL="http://localhost:$PORT/api/health"

COMMAND="${1:-}"
KEEP_DB=false
ASSUME_YES=false
SKIP_OLLAMA=false
TEST_FILTER=""
INCLUDE_MAILPIT=false   # set by 'loadtest' so outgoing mail is captured by Mailpit
COMPOSE_FILES=()

# Shift past the command arg, then scan remaining args for flags.
# The first non-flag argument is treated as the test filter (for the 'test' command).
[[ $# -gt 0 ]] && shift
for arg in "$@"; do
    case "$arg" in
        --keep-db)     KEEP_DB=true ;;
        --yes|-y)      ASSUME_YES=true ;;
        --skip-ollama) SKIP_OLLAMA=true ;;
        *)             TEST_FILTER="$arg" ;;
    esac
done

# --- helpers -----------------------------------------------------------------

get_gpu_vendor() {
    local os
    os="$(uname -s)"

    # macOS: Docker runs in a Linux VM - no GPU passthrough support on any Mac hardware
    if [[ "$os" == "Darwin" ]]; then
        echo "macos"
        return
    fi

    # Linux: nvidia-smi is the fastest check
    if command -v nvidia-smi &>/dev/null; then
        echo "nvidia"
        return
    fi

    # Linux fallback: query PCI devices
    if command -v lspci &>/dev/null; then
        local gpu_info
        gpu_info="$(lspci 2>/dev/null | grep -iE 'VGA|3D|Display')"
        if echo "$gpu_info" | grep -qi "NVIDIA"; then
            echo "nvidia"; return
        fi
        if echo "$gpu_info" | grep -qiE "AMD|Radeon|Advanced Micro"; then
            echo "amd"; return
        fi
    fi

    echo "none"
}

setup_compose_files() {
    COMPOSE_FILES=("-f" "$ROOT/docker-compose.yml")

    if [[ "$SKIP_OLLAMA" == "true" ]]; then
        echo "Ollama is skipped (--skip-ollama) - GPU detection and override disabled."
    else
        local gpu_vendor
        gpu_vendor="$(get_gpu_vendor)"
        case "$gpu_vendor" in
            nvidia)
                echo "GPU detected: NVIDIA - enabling GPU acceleration for Ollama."
                COMPOSE_FILES+=("-f" "$ROOT/docker-compose.gpu-nvidia.yml")
                ;;
            amd)
                echo "GPU detected: AMD - enabling GPU acceleration for Ollama (ROCm)."
                COMPOSE_FILES+=("-f" "$ROOT/docker-compose.gpu-amd.yml")
                ;;
            macos)
                echo "macOS: GPU passthrough is not supported in Docker. Ollama will run on CPU."
                ;;
            *)
                echo "No dedicated GPU detected - Ollama will run on CPU."
                ;;
        esac
    fi

    if [[ "$INCLUDE_MAILPIT" == "true" ]]; then
        echo "Mailpit override enabled - outgoing mail is captured locally (no real SMTP)."
        COMPOSE_FILES+=("-f" "$ROOT/docker-compose.mailpit.yml")
    fi
}

# Echoes the space-separated list of services to pass to `docker compose up`
# when --skip-ollama is set, so that Compose neither pulls nor starts the
# ollama service (and therefore does not download its image).
non_ollama_service_list() {
    local services="postgres backend prometheus blackbox-exporter grafana"
    if [[ "$INCLUDE_MAILPIT" == "true" ]]; then
        services="$services mailpit"
    fi
    echo "$services"
}

test_docker_running() {
    if ! docker info &>/dev/null; then
        echo "ERROR: Docker is not running. Start Docker Desktop and retry."
        return 1
    fi
    return 0
}

test_rebuild_needed() {
    # Compares the creation timestamp of the last built webshop-backend image against
    # tracked source files (src/, pom.xml, Dockerfile).
    # Returns 0 (rebuild needed) and prints the triggering filename.
    # Returns 1 (no rebuild needed or check skipped).
    # Requires python3 for cross-platform ISO 8601 timestamp parsing.

    command -v python3 &>/dev/null || return 1

    local image_id
    image_id="$(docker inspect webshop-backend --format '{{.Image}}' 2>/dev/null)" || return 1
    [[ -z "$image_id" ]] && return 1

    local created_str
    created_str="$(docker inspect "$image_id" --format '{{.Created}}' 2>/dev/null)" || return 1
    [[ -z "$created_str" ]] && return 1

    # Create a temp file whose mtime equals the image creation time (UTC epoch).
    # Use find -newer for a cross-platform file-age comparison.
    local tmp_file
    tmp_file="$(mktemp)"

    python3 - "$created_str" "$tmp_file" <<'PYEOF'
import sys, re, os, calendar
from datetime import datetime

ts  = sys.argv[1]
tmp = sys.argv[2]

# Truncate nanoseconds to microseconds
ts = re.sub(r'(\.\d{6})\d*', r'\1', ts)
# Strip timezone suffix - Docker timestamps are always UTC
ts = re.sub(r'(Z|[+-]\d{2}:\d{2})$', '', ts)

try:
    dt = datetime.strptime(ts, '%Y-%m-%dT%H:%M:%S.%f')
except ValueError:
    try:
        dt = datetime.strptime(ts, '%Y-%m-%dT%H:%M:%S')
    except ValueError:
        sys.exit(1)

# calendar.timegm interprets the struct_time as UTC (no DST conversion)
epoch = float(calendar.timegm(dt.timetuple()))
os.utime(tmp, (epoch, epoch))
PYEOF

    if [[ $? -ne 0 ]]; then
        rm -f "$tmp_file"
        return 1
    fi

    # Collect tracked paths that actually exist
    local tracked_paths=()
    for path in "$ROOT/src" "$ROOT/pom.xml" "$ROOT/Dockerfile"; do
        [[ -e "$path" ]] && tracked_paths+=("$path")
    done

    local changed_file
    changed_file="$(find "${tracked_paths[@]}" -newer "$tmp_file" ! -type d 2>/dev/null | head -1)"
    rm -f "$tmp_file"

    if [[ -n "$changed_file" ]]; then
        echo "Changed since last build: ${changed_file#"$ROOT/"}"
        return 0
    fi

    return 1
}

wait_for_backend() {
    printf "Waiting for backend to be ready..."
    local i
    for i in $(seq 1 90); do
        if curl -sf "$HEALTH_URL" &>/dev/null; then
            echo " ready."
            return 0
        fi
        printf "."
        sleep 2
    done
    echo " timed out!"
    echo "Check container logs: docker logs webshop-backend"
    return 1
}

show_seed_hint() {
    echo ""
    echo "--- Seed data (optional, run once) ---"
    echo "  docker exec -i webshop-postgres psql -U webshop -d webshop \\"
    echo "    < src/main/resources/db/dev-seed.sql"
    echo ""
    if [[ "$SKIP_OLLAMA" == "true" ]]; then
        echo "--- Shoppi KI-Assistent ---"
        echo "  Skipped (--skip-ollama). Shoppi answers with an 'unavailable' message."
    else
        echo "--- Shoppi KI-Assistent (Ollama model, run once) ---"
        echo "  docker exec webshop-ollama ollama pull gemma4:e4b"
        echo "  (This downloads ~10 GB on first run. Model is cached in the ollama_data volume.)"
    fi
    echo ""
    echo "--- Monitoring & Alerting ---"
    echo "  Grafana:    http://localhost:3001  (admin / admin)"
    echo "  Dashboards: JVM Overview, HTTP Requests, Spring Boot Overview"
    echo "  Prometheus scrapes backend:8081 internally - not reachable from outside Docker."
    echo ""
}

show_usage() {
    echo ""
    echo "Usage:  ./dev.sh <command> [--keep-db]"
    echo ""
    echo "Commands:"
    echo "  start    Start all containers (auto-detects if rebuild is needed)"
    echo "  stop     Stop all containers"
    echo "  restart  Stop backend, then start backend"
    echo "  rebuild  Force full rebuild - also deletes the PostgreSQL volume (fresh DB)"
    echo "           Use --keep-db to skip the volume deletion and keep existing data"
    echo "  test     Run the backend test suite in a Maven container (no local Maven needed)"
    echo "           Integration tests spin up PostgreSQL via Testcontainers (Docker required)"
    echo "           Optional filter: ./dev.sh test CartFlowIntegrationTest"
    echo "  loadtest Rebuild (fresh DB) + seed load-test data + run the k6 load test"
    echo "           DESTRUCTIVE: deletes the database. Asks for confirmation (skip with --yes)"
    echo ""
    echo "Options:"
    echo "  --keep-db      Keep PostgreSQL running (combine with stop/restart/rebuild)"
    echo "  --yes          Skip the loadtest confirmation prompt"
    echo "  --skip-ollama  Do not start (or pull) the Ollama container - Shoppi will be unavailable"
    echo "                 Works with start/restart/rebuild/loadtest"
    echo ""
}

read_command_interactive() {
    show_usage
    printf "Enter command (start/stop/restart/rebuild): "
    read -r input_command
    input_command="$(echo "$input_command" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

    case "$input_command" in
        start|stop|restart|rebuild|test|loadtest) ;;
        *)
            echo "ERROR: Unknown command '$input_command'."
            exit 1
            ;;
    esac

    printf "Keep PostgreSQL running? [y/N]: "
    read -r keep_db_answer
    keep_db_answer="$(echo "$keep_db_answer" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    if [[ "$keep_db_answer" == "y" || "$keep_db_answer" == "j" ]]; then
        KEEP_DB=true
    fi

    COMMAND="$input_command"
}

# --- stop --------------------------------------------------------------------

stop_backend() {
    if [[ "$KEEP_DB" == "true" ]]; then
        echo "Stopping backend container (keeping PostgreSQL running)..."
        docker compose -f "$ROOT/docker-compose.yml" stop backend
    else
        echo "Stopping all containers..."
        # --remove-orphans also tears down containers from overrides (e.g. mailpit) that
        # were started alongside the base stack but are not in this compose file.
        docker compose -f "$ROOT/docker-compose.yml" down --remove-orphans
    fi
}

# --- start -------------------------------------------------------------------

start_backend() {
    test_docker_running || return 1

    local rebuild_output
    if rebuild_output="$(test_rebuild_needed 2>&1)"; then
        [[ -n "$rebuild_output" ]] && echo "$rebuild_output"
        echo "Source changes detected since last build - rebuilding automatically."
        echo "-------------------------------------------------------------------------------"
        rebuild_backend
        return
    fi

    echo ""
    echo "Building and starting backend..."
    echo "(First run: Maven downloads dependencies inside the container - may take a few minutes)"
    echo "-------------------------------------------------------------------------------"

    setup_compose_files

    if [[ "$KEEP_DB" == "true" ]]; then
        docker compose "${COMPOSE_FILES[@]}" up -d --build backend
    elif [[ "$SKIP_OLLAMA" == "true" ]]; then
        local services
        services="$(non_ollama_service_list)"
        echo "Starting services without ollama (--skip-ollama): $services"
        # shellcheck disable=SC2086
        docker compose "${COMPOSE_FILES[@]}" up -d --build $services
    else
        docker compose "${COMPOSE_FILES[@]}" up -d --build
    fi

    if [[ $? -ne 0 ]]; then
        echo "ERROR: docker compose up failed."
        return 1
    fi

    if wait_for_backend; then
        echo "-------------------------------------------------------------------------------"
        echo "Backend is up at     http://localhost:$PORT"
        echo "Swagger UI:          http://localhost:$PORT/swagger-ui/index.html"
        show_seed_hint
        echo "Run './dev.sh stop' to shut down."
    fi
}

# --- rebuild -----------------------------------------------------------------

rebuild_backend() {
    echo "Forcing full rebuild (no layer cache)..."
    test_docker_running || return 1

    setup_compose_files

    if [[ "$KEEP_DB" == "true" ]]; then
        echo "Keeping PostgreSQL data (--keep-db)."
        docker compose "${COMPOSE_FILES[@]}" up -d --build --force-recreate backend
    else
        docker compose "${COMPOSE_FILES[@]}" down --remove-orphans
        local postgres_volume
        postgres_volume="$(docker volume ls --format '{{.Name}}' | grep 'postgres_data' | head -1)"
        if [[ -n "$postgres_volume" ]]; then
            echo "Removing PostgreSQL volume ($postgres_volume) for a clean database..."
            docker volume rm "$postgres_volume"
        fi
        if [[ "$SKIP_OLLAMA" == "true" ]]; then
            local services
            services="$(non_ollama_service_list)"
            echo "Starting services without ollama (--skip-ollama): $services"
            # shellcheck disable=SC2086
            docker compose "${COMPOSE_FILES[@]}" up -d --build --force-recreate $services
        else
            docker compose "${COMPOSE_FILES[@]}" up -d --build --force-recreate
        fi
    fi

    if [[ $? -ne 0 ]]; then
        echo "ERROR: docker compose rebuild failed."
        return 1
    fi

    if wait_for_backend; then
        echo "-------------------------------------------------------------------------------"
        echo "Backend is up at     http://localhost:$PORT"
        echo "Swagger UI:          http://localhost:$PORT/swagger-ui/index.html"
        show_seed_hint
        echo "Run './dev.sh stop' to shut down."
    fi
}

# --- test --------------------------------------------------------------------

run_tests() {
    test_docker_running || return 1

    echo "Running backend tests in a Maven container (no local Java/Maven needed)..."
    echo "Integration tests start a PostgreSQL container via Testcontainers using the host Docker daemon."
    [[ -n "$TEST_FILTER" ]] && echo "Filter: -Dtest=$TEST_FILTER"
    echo "(First run downloads dependencies into the cached volume and pulls the postgres image.)"
    echo "-------------------------------------------------------------------------------"

    local maven_args=("-B" "test")
    [[ -n "$TEST_FILTER" ]] && maven_args+=("-Dtest=$TEST_FILTER")

    # The mounted Docker socket lets Testcontainers start sibling containers;
    # the host-gateway mapping plus TESTCONTAINERS_HOST_OVERRIDE let the Maven container
    # reach the mapped database port on the host (works on Docker Desktop and native Linux).
    docker run --rm \
        -v "$ROOT:/app" \
        -v webshop-mvn-repo:/root/.m2 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        --add-host=host.docker.internal:host-gateway \
        -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
        -w /app \
        maven:3.9-eclipse-temurin-21-alpine \
        mvn "${maven_args[@]}"

    local exit_code=$?
    echo "-------------------------------------------------------------------------------"
    if [[ $exit_code -ne 0 ]]; then
        echo "ERROR: Tests failed (exit code $exit_code)."
        return 1
    fi
    echo "All tests passed."
}

# --- loadtest ----------------------------------------------------------------

import_seed_file() {
    local relative_path="$1"
    local label="$2"
    local seed_path="$ROOT/$relative_path"
    if [[ ! -f "$seed_path" ]]; then
        echo "ERROR: Seed file not found: $seed_path"
        return 1
    fi
    echo "Seeding $label ..."
    docker exec -i webshop-postgres psql -U webshop -d webshop < "$seed_path"
}

run_k6_script() {
    local script_name="$1"
    local place_orders="$2"   # "true" / "false"

    echo "-------------------------------------------------------------------------------"
    echo "Running k6: $script_name  (real orders: $([[ "$place_orders" == "true" ]] && echo yes || echo no))"
    echo "Live k6 output follows. Watch Grafana http://localhost:3001, Mailpit http://localhost:8025"
    echo "-------------------------------------------------------------------------------"

    local extra_env=()
    [[ "$place_orders" == "true" ]] && extra_env=(-e PLACE_ORDERS=true)

    # Foreground docker run → k6's progress and final report stream straight to this console.
    docker run --rm \
        -e "BASE_URL=http://host.docker.internal:$PORT" \
        -e LOGIN_USER_PREFIX=loaduser \
        -e LOGIN_USER_COUNT=1000 \
        "${extra_env[@]}" \
        --add-host=host.docker.internal:host-gateway \
        -v "$ROOT/loadtest:/scripts" \
        grafana/k6 run "/scripts/$script_name"
}

run_loadtest() {
    test_docker_running || return 1

    if [[ "$ASSUME_YES" != "true" ]]; then
        echo "WARNING: 'loadtest' rebuilds the stack and DELETES the PostgreSQL database,"
        echo "         then seeds load-test data."
        printf "Continue? [y/N]: "
        read -r answer
        answer="$(echo "$answer" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
        if [[ "$answer" != "y" && "$answer" != "j" ]]; then
            echo "Aborted."
            return 0
        fi
    fi

    # Fresh database + Mailpit override (captures all outgoing mail locally; no real SMTP).
    KEEP_DB=false
    INCLUDE_MAILPIT=true
    rebuild_backend
    if ! wait_for_backend; then
        echo "ERROR: Backend did not become ready — aborting load test."
        return 1
    fi

    import_seed_file "src/main/resources/db/dev-seed.sql" "base data (dev-seed)"
    import_seed_file "src/main/resources/db/loadtest-seed.sql" "load-test data"

    echo ""
    echo "Backend is ready and seeded."
    printf "Lasttest jetzt starten? [j]a / [n]ein / [a] ja inkl. Bestellungen (max. Auslastung): "
    read -r choice
    choice="$(echo "$choice" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

    local orders_included=false
    local read_test_ran=false
    case "$choice" in
        a|alles|b|jainkl|ja-inkl)
            run_k6_script "load-test.js" "true"
            orders_included=true
            read_test_ran=true
            ;;
        j|ja|y|"")
            run_k6_script "load-test.js" "false"
            read_test_ran=true
            ;;
        *)
            echo "Lasttest uebersprungen."
            ;;
    esac

    if [[ "$read_test_ran" == "true" && "$orders_included" != "true" ]]; then
        printf "Jetzt noch ein reines Bestell-Lastszenario ausfuehren? [j/N]: "
        read -r order_choice
        order_choice="$(echo "$order_choice" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
        if [[ "$order_choice" == "j" || "$order_choice" == "ja" || "$order_choice" == "y" ]]; then
            run_k6_script "order-load-test.js" "false"
        fi
    fi

    echo "-------------------------------------------------------------------------------"
    echo "Done. The stack is still running for inspection:"
    echo "  Grafana: http://localhost:3001  (admin / admin)"
    echo "  Mailpit: http://localhost:8025  (captured emails)"
    echo "Run './dev.sh stop' to shut down."
}

# --- dispatch ----------------------------------------------------------------

if [[ -z "$COMMAND" ]]; then
    read_command_interactive
fi

case "$COMMAND" in
    start)
        start_backend
        ;;
    stop)
        stop_backend
        ;;
    restart)
        stop_backend
        sleep 1
        start_backend
        ;;
    rebuild)
        rebuild_backend
        ;;
    test)
        run_tests
        ;;
    loadtest)
        run_loadtest
        ;;
    *)
        echo "ERROR: Unknown command '$COMMAND'. Run './dev.sh' without arguments for help."
        exit 1
        ;;
esac
