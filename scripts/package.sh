#!/usr/bin/env bash
# Genera el instalador nativo del SO actual con jpackage (.dmg en macOS, .deb en Linux).
#   TYPE=app-image ./scripts/package.sh   -> carpeta ejecutable sin instalar
#   SKIP_BUILD=1 ./scripts/package.sh     -> reutiliza target/ en lugar de llamar a Maven
# Requiere JDK 17+ (jpackage); Maven solo si no se usa SKIP_BUILD; en Linux además fakeroot y dpkg.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${SKIP_BUILD:-}" ]]; then
  mvn -B -DskipTests package
fi

# jpackage solo admite versiones numéricas; en macOS la versión no puede empezar por 0.
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n1 | sed 's/-SNAPSHOT//')"
JAR="$(basename "$(ls target/text-refinery-*.jar | head -n1)")"

if [[ "$(uname)" == "Darwin" ]]; then
  TYPE="${TYPE:-dmg}"
  ICON="src/main/resources/icons/app.icns"
  [[ "$VERSION" == 0.* ]] && VERSION="1.${VERSION#0.}"
else
  TYPE="${TYPE:-deb}"
  ICON="src/main/resources/icons/app.png"
fi

# jpackage exige que el jar principal esté dentro de --input
STAGING="target/jpackage-input"
rm -rf "$STAGING" && mkdir -p "$STAGING"
cp target/libs/* "$STAGING"/
cp "target/$JAR" "$STAGING"/

jpackage --type "$TYPE" \
  --name "Text Refinery" \
  --app-version "$VERSION" \
  --description "Limpia y refina el texto del portapapeles" \
  --vendor "Text Refinery" \
  --input "$STAGING" \
  --main-jar "$JAR" \
  --main-class com.textrefinery.App \
  --add-modules java.se,jdk.unsupported \
  --icon "$ICON" \
  --dest dist

echo "Listo: carpeta dist/"
