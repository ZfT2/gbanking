#!/usr/bin/env sh

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
LIB="$APP_HOME/lib"

if ! command -v java >/dev/null 2>&1; then
    echo "Fehler: Java wurde nicht gefunden."
    echo "Bitte installiere Java 17 oder neuer und stelle sicher, dass 'java' im PATH verfuegbar ist."
    exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "Warnung: Es wurde kein Java 17 oder neuer erkannt."
    echo "Die Anwendung wurde fuer Java 17 gebaut und startet moeglicherweise nicht korrekt."
fi

exec java --module-path "$LIB" --add-modules javafx.controls -cp "$LIB/*" de.zft2.gbanking.GBanking "$@"
