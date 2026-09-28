#!/bin/bash
# Install Apache Ant and the Ivy jar on a GitHub Actions runner and export IVY_JAR.
set -euo pipefail

IVY_VERSION=2.5.3
# Ant is preinstalled on most runner images
if ! command -v ant >/dev/null 2>&1; then
  case "$(uname -s)" in
  Linux) sudo apt-get update -qq && sudo apt-get install -y -qq ant ;;
  Darwin) brew install ant ;;
  *) choco install ant -y --no-progress ;;
  esac
fi
ant -version

mkdir -p "$HOME/.ivy-lib"
curl -fsSL -o "$HOME/.ivy-lib/ivy.jar" "https://repo1.maven.org/maven2/org/apache/ivy/ivy/$IVY_VERSION/ivy-$IVY_VERSION.jar"
echo "IVY_JAR=$HOME/.ivy-lib/ivy.jar" >> "$GITHUB_ENV"
