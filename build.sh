#!/usr/bin/env bash
# ForgeWorldGen v3 direct-javac build.
#
# WHY: this sandbox blocks the Gradle daemon's loopback TCP, so Gradle cannot
# run here at all. This script compiles with javac directly against the jars in
# ~/workspace/.toolchains/paper-deps and packages the jar. Keep the Paper API
# version below in sync with plugin.yml's api-version.
set -euo pipefail

export JAVA_HOME="$HOME/workspace/.toolchains/jdk-25.0.4.1+1"
DEPS="$HOME/workspace/.toolchains/paper-deps"
SRC="$HOME/workspace/forge-worldgen-v3"
OUT="$SRC/build"
VERSION="3.1.0"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
CP=$(ls "$DEPS"/*.jar | tr '\n' ':')

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/stage"
find "$SRC/src/main/java" -name '*.java' > "$OUT/sources.txt"
$JAVAC -Werror -Xlint:deprecation -parameters -d "$OUT/classes" -cp "$CP" @"$OUT/sources.txt"
cp -r "$OUT/classes"/. "$OUT/stage"/
cp "$SRC/src/main/resources/plugin.yml" "$SRC/src/main/resources/config.yml" "$OUT/stage"/
( cd "$OUT/stage" && $JAR --create --file "$SRC/ForgeWorldGen-$VERSION.jar" . )
echo "built ForgeWorldGen-$VERSION.jar"
