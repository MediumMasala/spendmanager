#!/bin/bash

# SpendManager Android Release Build Script
# This script helps generate a signed release APK/AAB

set -e

echo "=== SpendManager Release Build ==="
echo ""

# Check if keystore exists
if [ ! -f "release-keystore.jks" ]; then
    echo "⚠️  No keystore found. Generating new keystore..."
    echo ""
    echo "Please enter your details for the signing certificate:"

    keytool -genkeypair \
        -v \
        -storetype JKS \
        -keystore release-keystore.jks \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -alias spendmanager

    echo ""
    echo "✅ Keystore generated: release-keystore.jks"
    echo ""
    echo "⚠️  IMPORTANT: Back up this keystore file securely!"
    echo "   You will need it for all future Play Store updates."
    echo ""
fi

# Check if keystore.properties exists
if [ ! -f "keystore.properties" ]; then
    echo "⚠️  keystore.properties not found."
    echo ""
    echo "Please create keystore.properties with your passwords:"
    echo ""
    echo "  cp keystore.properties.template keystore.properties"
    echo "  # Then edit keystore.properties with your passwords"
    echo ""
    exit 1
fi

echo "Building release APK and AAB..."
echo ""

# Clean previous builds
./gradlew clean

# Build Play Store release (AAB for Play Store upload)
echo "Building Play Store AAB..."
./gradlew bundlePlayRelease

# Build Play Store APK (for testing)
echo "Building Play Store APK..."
./gradlew assemblePlayRelease

# Build Sideload APK (with SMS ingestion enabled)
echo "Building Sideload APK..."
./gradlew assembleSideloadRelease

echo ""
echo "=== Build Complete ==="
echo ""
echo "Output files:"
echo "  Play Store AAB: app/build/outputs/bundle/playRelease/app-play-release.aab"
echo "  Play Store APK: app/build/outputs/apk/play/release/app-play-release.apk"
echo "  Sideload APK:   app/build/outputs/apk/sideload/release/app-sideload-release.apk"
echo ""
echo "Next steps:"
echo "  1. Test the APK on a real device"
echo "  2. Upload the AAB to Google Play Console"
echo ""
