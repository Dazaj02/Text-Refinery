#!/usr/bin/env bash
# Genera el instalador nativo del SO actual (.dmg en macOS, .deb en Linux). Requiere JDK 21+ y Maven.
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -B -DskipTests package
VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT//')"
JAR="$(basename "$(ls target/text-refinery-*.jar | head -n1)")"

# jpackage solo acepta versiones numéricas; en macOS la versión no puede empezar por 0.
if [[ "$(uname)" == "Darwin" ]]; then
  TYPE=dmg
  [[ "$VERSION" == 0.* ]] && VERSION="1.${VERSION#0.}"
else
  TYPE=deb
fi

jpackage --type "$TYPE" \
  --name "Text Refinery" \
  --app-version "$VERSION" \
  --input target/libs \
  --main-jar "../$JAR" \
  --main-class com.textrefinery.App \
  --dest dist
