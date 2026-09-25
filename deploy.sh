#!/usr/bin/env bash
# Tanvrit Accounting — manual deploy (WEB via Cloudflare Pages).
#
# The preferred path is CI. This script is the outage fallback. It builds the
# WasmJS production bundle and deploys it with `wrangler pages deploy`.
#
# Usage:
#   ./deploy.sh web          # build wasmJs production + deploy to Pages
#   ./deploy.sh web --dry-run  # build only, no deploy
#
# Requires: CLOUDFLARE_ACCOUNT_ID (+ CLOUDFLARE_API_TOKEN, or a wrangler
# OAuth session). Set CF_PROJECT to override the default Pages project name.
set -euo pipefail

cd "$(dirname "$0")"

CF_PROJECT="${CF_PROJECT:-tanvrit-accounting}"
DRY_RUN=0
TARGET="${1:-}"
shift || true
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
    esac
done

if [ "$TARGET" != "web" ]; then
    echo "usage: ./deploy.sh web [--dry-run]" >&2
    echo "(desktop artifacts: ./gradlew :composeApp:packageDistributionForCurrentOS)" >&2
    exit 64
fi

echo "==> Building WasmJS production bundle"
./gradlew :composeApp:wasmJsBrowserProductionWebpack

DIST_DIR="composeApp/build/dist/wasmJs/productionExecutable"
echo "==> Bundle at $DIST_DIR"

if [ "$DRY_RUN" = "1" ]; then
    echo "==> dry-run: skipping Cloudflare Pages deploy"
    exit 0
fi

: "${CLOUDFLARE_ACCOUNT_ID:?CLOUDFLARE_ACCOUNT_ID is required for deploy}"

echo "==> Deploying to Cloudflare Pages project: $CF_PROJECT"
npx wrangler pages deploy "$DIST_DIR" --project-name "$CF_PROJECT" --branch main

echo "==> Done. Verify the live URL serves THIS build, not the previous deploy."
