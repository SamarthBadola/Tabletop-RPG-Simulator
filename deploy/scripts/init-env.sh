#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"

if ! command -v openssl >/dev/null 2>&1; then
    printf 'OpenSSL is required to generate a database password.\\n' >&2
    exit 1
fi

umask 077
if [[ -e "$ENV_FILE" ]]; then
    if grep -q '^APP_INSTANCE_KEY=.' "$ENV_FILE"; then
        printf 'Found %s; leaving it unchanged.\\n' "$ENV_FILE"
        exit 0
    fi
    TEMP_FILE="$(mktemp "$DEPLOY_DIR/.env.XXXXXX")"
    trap 'rm -f "$TEMP_FILE"' EXIT
    sed '/^APP_INSTANCE_KEY=/d' "$ENV_FILE" > "$TEMP_FILE"
    printf '\\nAPP_INSTANCE_KEY=%s\\n' "$(openssl rand -hex 32)" >> "$TEMP_FILE"
    mv "$TEMP_FILE" "$ENV_FILE"
    trap - EXIT
    printf 'Added an instance key to %s.\\n' "$ENV_FILE"
    exit 0
fi
DB_PASSWORD="$(openssl rand -hex 32)"
APP_INSTANCE_KEY="$(openssl rand -hex 32)"
TEMP_FILE="$(mktemp "$DEPLOY_DIR/.env.XXXXXX")"
trap 'rm -f "$TEMP_FILE"' EXIT

cat > "$TEMP_FILE" <<EOF
RPG_VERSION=0.1.0
DB_PASSWORD=$DB_PASSWORD
APP_INSTANCE_KEY=$APP_INSTANCE_KEY
APP_COOKIE_SECURE=false
PATHFINDER_MODE=java
EOF

chmod 600 "$TEMP_FILE"
mv "$TEMP_FILE" "$ENV_FILE"
trap - EXIT
printf 'Created %s with a random database password.\\n' "$ENV_FILE"
