# Gbanking
Java-basiertes Homebanking Programm für das FinTS Protokoll

## Voraussetzungen
- Installiertes Java 21
- Der Befehl "java" muss im PATH verfügbar sein

## Start
- Windows: bin\gbanking.bat
- Linux:   bin/gbanking.sh
- macOS:   bin/gbanking.command

## Hinweise
- Dieses Paket enthält die Anwendung und ihre Bibliotheken
- Es wird keine eigene JRE/JDK mitgeliefert
- Bei einer anderen Java-Version als 21 kann der Start fehlschlagen

## Lizenz

Dieses Projekt steht unter der **GNU General Public License v3.0**.

Siehe Datei `LICENSE` im Projektverzeichnis.

## Source Code

Der vollständige Source Code ist verfügbar unter:

https://github.com/ZfT2/gbanking



# gbanking
Java-based Banking Program using the FinTS protocol

Source Code
The source code of this program is available at:
https://github.com/ZfT2/gbanking

License
This project is licensed under the GNU General Public License v3.0.
See the LICENSE file for details.


## Build distribution archives

### Windows
```bash
mvn -Pwindows clean package
```

### Linux
```bash
mvn -Plinux clean package
```

### Mac
```bash
mvn -Pmac clean package
```

## Releases

Binary releases are published on GitHub Releases.

Each release contains platform-specific archives:

Windows: .zip

Linux: .tar.gz

macOS: .tar.gz
