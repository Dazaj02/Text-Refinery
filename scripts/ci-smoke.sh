#!/usr/bin/env bash
# Arranque real de la app (sin pulsar nada): comprueba que la JVM, JavaFX, la bandeja y el atajo global
# no revientan en este sistema y que app.log queda sin excepciones no controladas.
#   Uso: scripts/ci-smoke.sh [ruta-al-jar]      (por defecto target/text-refinery-*.jar)
# En Linux sin pantalla se ejecuta bajo xvfb: scripts/ci-smoke.sh con "xvfb-run -a".
set -euo pipefail
cd "$(dirname "$0")/.."

JAR="${1:-$(ls target/text-refinery-*.jar | head -n1)}"
case "$(uname -s)" in
  Darwin) LOG="$HOME/Library/Application Support/TextRefinery/app.log" ;;
  MINGW*|MSYS*|CYGWIN*) LOG="$APPDATA/TextRefinery/app.log" ;;
  *) LOG="${XDG_CONFIG_HOME:-$HOME/.config}/text-refinery/app.log" ;;
esac
rm -f "$LOG" "$LOG.1"

echo "Arrancando $JAR ..."
java -jar "$JAR" --settings > /tmp/text-refinery-smoke.out 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT

READY=""
for _ in $(seq 1 60); do
  if grep -qs "Text Refinery listo" "$LOG" 2>/dev/null; then READY=1; break; fi
  if ! kill -0 $PID 2>/dev/null; then break; fi
  sleep 1
done

echo "----- app.log -----"
cat "$LOG" 2>/dev/null || echo "(no se creó $LOG)"
echo "----- salida de la consola -----"
cat /tmp/text-refinery-smoke.out || true

if [[ -z "$READY" ]]; then echo "FALLO: la app no llegó a 'Text Refinery listo'"; exit 1; fi
if grep -qs "Excepción no controlada" "$LOG"; then echo "FALLO: excepción no controlada en app.log"; exit 1; fi
if ! kill -0 $PID 2>/dev/null; then echo "FALLO: la app terminó sola"; exit 1; fi
echo "OK: la app arrancó y sigue viva"
