#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
JAVA_SOURCE="$SCRIPT_DIR/GenerateWeatherIcons.java"
BACKGROUND_DIR="$SCRIPT_DIR/weather-backgrounds"
OUTPUT_DIR="$PROJECT_DIR/app/src/main/res/mipmap-nodpi"
MANIFEST_FILE="$PROJECT_DIR/app/src/main/AndroidManifest.xml"

if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAVA="$JAVA_HOME/bin/java"
else
  JAVAC=""
  JAVA=""
  WORKSPACE_JDK_DIR="$PROJECT_DIR/../../work/tools/jdk"
  if [[ -d "$WORKSPACE_JDK_DIR" ]]; then
    for jdk in "$WORKSPACE_JDK_DIR"/*; do
      [[ -d "$jdk" ]] || continue
      if [[ -x "$jdk/bin/javac" && -x "$jdk/bin/java" ]]; then
        JAVAC="$jdk/bin/javac"
        JAVA="$jdk/bin/java"
        break
      elif [[ -x "$jdk/bin/javac.exe" && -x "$jdk/bin/java.exe" ]]; then
        JAVAC="$jdk/bin/javac.exe"
        JAVA="$jdk/bin/java.exe"
        break
      fi
    done
  fi
  if [[ -z "$JAVAC" ]]; then
    JAVAC="$(command -v javac || true)"
    JAVA="$(command -v java || true)"
  fi
fi

if [[ -z "$JAVAC" || ! -x "$JAVAC" || -z "$JAVA" || ! -x "$JAVA" ]]; then
  echo "JDK 17 or newer is required. Set JAVA_HOME or add javac and java to PATH." >&2
  exit 1
fi

VERSION_LINE="$("$JAVAC" -version 2>&1)"
VERSION="${VERSION_LINE#javac }"
MAJOR="${VERSION%%.*}"
if [[ ! "$MAJOR" =~ ^[0-9]+$ ]] || (( MAJOR < 17 )); then
  echo "JDK 17 or newer is required; found: $VERSION_LINE" >&2
  exit 1
fi

CLASSES_DIR="$(mktemp -d "${TMPDIR:-/tmp}/weather-icon-generator.XXXXXX")"
trap 'rm -rf "$CLASSES_DIR"' EXIT
"$JAVAC" --release 17 -d "$CLASSES_DIR" "$JAVA_SOURCE"
"$JAVA" -cp "$CLASSES_DIR" GenerateWeatherIcons "$BACKGROUND_DIR" "$OUTPUT_DIR" "$MANIFEST_FILE"
