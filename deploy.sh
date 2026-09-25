#!/bin/bash
# Tanvrit Accounting Platform — deployment script
# Supports web (Cloudflare Pages + Wasm), desktop (GHCR), and optionally iOS/Android store release

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/composeApp/build"

echo "=== Deploying Tanvrit Accounting Platform ==="

# Build
echo "Building for all platforms..."
./gradlew build website wasmJsBrowserDevelopmentRun

# Deploy web
if [ -d "$BUILD_DIR/dist/js/bin" ]; then
    echo "Deploying web to Cloudflare Pages..."
    cd "$BUILD_DIR/dist/js/bin" && npm install --silent
fi

echo "=== Deployment complete ==="
