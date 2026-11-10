#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
TOOLS_DIR="$PROJECT_DIR/../../work/tools"

if [[ -z "${JAVA_HOME:-}" && -d "$TOOLS_DIR/jdk" ]]; then
  for jdk in "$TOOLS_DIR"/jdk/*; do
    if [[ -x "$jdk/bin/java" ]]; then
      export JAVA_HOME="$jdk"
      break
    fi
  done
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

LOCAL_GRADLE="$TOOLS_DIR/gradle-8.11.1/bin/gradle"
if [[ -x "$LOCAL_GRADLE" ]]; then
  GRADLE="$LOCAL_GRADLE"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="$(command -v gradle)"
else
  echo "Gradle 8.11.1 was not found. Install Gradle or place it in work/tools beside the project." >&2
  exit 1
fi

cd "$PROJECT_DIR"
"$GRADLE" :app:assembleRelease
echo "Release APK: $PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
