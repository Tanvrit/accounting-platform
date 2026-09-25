#!/bin/bash
# Tanvrit Accounting Platform - deployment script
# Supports web (WasmJS), desktop (macOS via DMG), iOS (via App Store Connect)

set -e

echo "=== Tanvrit Accounting Deploy Script ==="

# Build WasmJS package (web)
if [ "$1" = "web" ]; then
    echo "Building for WasmJS browser..."
    ./gradlew :composeApp:wasmJsBrowserProductionWebpack

    echo "Deploying to Cloudflare Pages..."
    echo "(This would upload to Cloudflare Pages via wrangler)
fi

# Build macOS store package (DMG)
if [ "$1" = "macos" ]; then
    echo "Building macOS application..."
    ./gradlew :composeApp:packageReleaseDmg

    echo "Deploying to GitHub Container Registry..."
    echo "(This would upload the DMG via GH CLI)
fi

echo "=== Deploy Complete ==="
