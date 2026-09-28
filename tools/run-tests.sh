#!/bin/bash
# Compile and run the unit tests that need no network, API keys or native libraries.
# Usage: tools/run-tests.sh   (after ant fatjar)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

CP="dist/ReNameo_1.0.0.jar${SEP}lib/ivy/jar/junit.jar${SEP}lib/ivy/jar/hamcrest-core.jar${SEP}lib/ivy/jar/hamcrest-library.jar"
OUT="build/test-classes"

rm -rf "$OUT"
mkdir -p "$OUT"
"$JAVAC" -nowarn -encoding utf-8 -cp "$CP" -d "$OUT" $(find test -name "*.java")

SUITES=(
  net.renameo.similarity.SimilarityTestSuite
  net.renameo.similarity.EpisodeMetricsTest
  net.renameo.util.UtilTestSuite
  net.renameo.media.ReleaseInfoTest
  net.renameo.media.VideoFormatTest
  net.renameo.media.MediaDetectionTest
  net.renameo.format.ExpressionFormatTest
  net.renameo.ui.rename.MatchModelTest
  net.renameo.subtitle.SubtitleReaderTestSuite
  net.renameo.hash.VerificationFormatTest
)

"$JAVA" -Djava.awt.headless=true -cp "$OUT${SEP}$CP" org.junit.runner.JUnitCore "${SUITES[@]}"
