# GBanking

> ⚠ **Alpha Status**
>
> **Dieses Projekt befindet sich aktuell im Alpha-Stadium.**
>
> Die Anwendung ist noch **nicht vollständig implementiert und nicht für produktive Nutzung geeignet**.
>
> Insbesondere sollten aktuell **keine echten Bankzugänge oder produktiven Konten verwendet werden**.

![Build](https://github.com/ZfT2/gbanking/actions/workflows/release.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/ZfT2/gbanking)
![Java](https://img.shields.io/badge/Java-21-blue)
![License](https://img.shields.io/badge/License-GPLv3-blue)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)

🇩🇪 [Deutsch](#deutsch) | 🇬🇧 [English](#english)

---

# Deutsch

## Überblick

GBanking ist eine Desktop-Banking-Anwendung auf Basis von **JavaFX** und **HBCI/FinTS**.

Mit dieser Anwendung können Bankkonten über das HBCI/FinTS-Protokoll verwaltet werden.

Der Fokus des Projekts liegt auf:

- einer einfache Desktop-Anwendung
- plattformübergreifender Nutzung
- Open-Source-Lizenzierung

## Funktionen

Der aktuelle Entwicklungsstand umfasst unter anderem:

- Verwaltung von Bankkonten
- Zugriff auf Kontoumsätze über HBCI/FinTS
- Import und Verarbeitung von Bankdaten
- Desktop-GUI auf Basis von JavaFX

## Voraussetzungen

Für den Betrieb wird benötigt:

- **Java 21**
- `java` muss im `PATH` verfügbar sein

Die Anwendung liefert **keine eigene JRE** mit.

## Anwendung starten

Nach dem Entpacken eines Release-Archivs:

### Windows

```
bin\gbanking.bat
```

### Linux

```
bin/gbanking.sh
```

### macOS

```
bin/gbanking.command
```

## Releases

Vorgefertigte Binärpakete sind auf GitHub verfügbar:

https://github.com/ZfT2/gbanking/releases

Jedes Release enthält plattformspezifische Archive:

| Plattform | Format |
|-----------|--------|
| Windows | `.zip` |
| Linux | `.tar.gz` |
| macOS | `.tar.gz` |

## Projekt selbst bauen

Voraussetzung: Maven und Java 21.

### Windows Distribution

```
mvn -Pwindows clean package
```

### Linux Distribution

```
mvn -Plinux clean package
```

### macOS Distribution

```
mvn -Pmac clean package
```

Die erzeugten Archive befinden sich anschließend im Verzeichnis:

```
target/
```

## Projektstruktur (vereinfacht)

```
gbanking
├─ src
│  ├─ main
│  │  ├─ java
│  │  └─ resources
│  └─ test
├─ src/assembly
│  ├─ windows.xml
│  ├─ linux.xml
│  ├─ mac.xml
│  └─ */bin
├─ .github/workflows
│  └─ release.yml
├─ pom.xml
├─ README.md
├─ CHANGELOG.md
└─ LICENSE
```

## Entwicklung

Beiträge sind willkommen.

Siehe:

```
CONTRIBUTING.md
```

## Screenshot

*(optional – kann später ergänzt werden)*

```
docs/screenshot.png
```

## Lizenz

Dieses Projekt steht unter der

**GNU General Public License v3.0**

Siehe Datei:

```
LICENSE
```

---

# English

## Overview

GBanking is a desktop banking application based on **JavaFX** and **HBCI/FinTS**.

The project provides a free banking client for managing bank accounts via the HBCI/FinTS protocol.

The project focuses on:

- a simple desktop application
- cross-platform compatibility
- open source licensing

## Features

Current development includes:

- Bank account management
- Access to account transactions via HBCI/FinTS
- Import and processing of banking data
- Desktop GUI built with JavaFX

## Requirements

To run the application you need:

- **Java 21**
- `java` available in the system `PATH`

The application **does not bundle its own JRE**.

## Starting the application

After extracting a release archive:

### Windows

```
bin\gbanking.bat
```

### Linux

```
bin/gbanking.sh
```

### macOS

```
bin/gbanking.command
```

## Releases

Pre-built binaries are available on GitHub:

https://github.com/ZfT2/gbanking/releases

Each release contains platform specific archives:

| Platform | Format |
|----------|--------|
| Windows | `.zip` |
| Linux | `.tar.gz` |
| macOS | `.tar.gz` |

## Building from source

Requirements: Maven and Java 21.

### Windows distribution

```
mvn -Pwindows clean package
```

### Linux distribution

```
mvn -Plinux clean package
```

### macOS distribution

```
mvn -Pmac clean package
```

Build artifacts will be generated in:

```
target/
```

## Contributing

Contributions are welcome.

Please see:

```
CONTRIBUTING.md
```

## Screenshot

*(optional – may be added later)*

```
docs/screenshot.png
```

## License

This project is licensed under the

**GNU General Public License v3.0**

See the file:

```
LICENSE
```