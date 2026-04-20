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
#   rebuild -> docker compose up -d --build --force-recreate

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080
HEALTH_URL="http://localhost:$PORT/api/health"

COMMAND="${1:-}"
KEEP_DB=false
COMPOSE_FILES=()

# Shift past the command arg, then scan remaining args for flags
[[ $# -gt 0 ]] && shift
for arg in "$@"; do
    [[ "$arg" == "--keep-db" ]] && KEEP_DB=true
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
    echo "--- Shoppi KI-Assistent (Ollama model, run once) ---"
    echo "  docker exec webshop-ollama ollama pull gemma4:e4b"
    echo "  (This downloads ~10 GB on first run. Model is cached in the ollama_data volume.)"
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
    echo "  rebuild  Force full rebuild (recreates containers)"
    echo ""
    echo "Options:"
    echo "  --keep-db   Keep PostgreSQL running (combine with stop/restart/rebuild)"
    echo ""
}

read_command_interactive() {
    show_usage
    printf "Enter command (start/stop/restart/rebuild): "
    read -r input_command
    input_command="$(echo "$input_command" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

    case "$input_command" in
        start|stop|restart|rebuild) ;;
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
        docker compose -f "$ROOT/docker-compose.yml" down
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
        docker compose "${COMPOSE_FILES[@]}" up -d --build --force-recreate backend
    else
        docker compose "${COMPOSE_FILES[@]}" down
        docker compose "${COMPOSE_FILES[@]}" up -d --build --force-recreate
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
    *)
        echo "ERROR: Unknown command '$COMMAND'. Run './dev.sh' without arguments for help."
        exit 1
        ;;
esac
