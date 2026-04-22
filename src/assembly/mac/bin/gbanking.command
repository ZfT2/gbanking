#!/usr/bin/env sh

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
LIB="$APP_HOME/lib"

if ! command -v java >/dev/null 2>&1; then
    echo "Fehler: Java wurde nicht gefunden."
    echo "Bitte installiere Java 21 und stelle sicher, dass 'java' im PATH verfuegbar ist."
    exit 1
fi

JAVA_VERSION_OUTPUT="$(java -version 2>&1)"
case "$JAVA_VERSION_OUTPUT" in
    *\"21.*)
        ;;
    *)
        echo "Warnung: Es wurde kein Java 21 erkannt."
        echo "Die Anwendung wurde fuer Java 21 gebaut und startet moeglicherweise nicht korrekt."
        ;;
esac

exec java --module-path "$LIB" --add-modules javafx.controls -cp "$LIB/*" de.zft2.gbanking.GBanking "$@"