#!/bin/sh
# Self-contained Gradle launcher (no gradle-wrapper.jar needed).
# Downloads Gradle once, caches it, then runs it on this project.
set -e
GRADLE_VERSION=8.7
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap"
DIST_DIR="$BASE/gradle-$GRADLE_VERSION"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  echo "Downloading Gradle $GRADLE_VERSION ..."
  mkdir -p "$BASE"
  TMP=$(mktemp -d)
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$TMP/gradle.zip"
  else
    wget -q "$URL" -O "$TMP/gradle.zip"
  fi
  unzip -q "$TMP/gradle.zip" -d "$TMP"
  rm -rf "$DIST_DIR"
  mv "$TMP/gradle-$GRADLE_VERSION" "$DIST_DIR"
  rm -rf "$TMP"
fi

exec "$DIST_DIR/bin/gradle" -p "$APP_HOME" "$@"
