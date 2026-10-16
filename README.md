# Litematica Material Swap

Client-seitiger Fabric-Mod für Minecraft 26.3, der Litematica erweitert: In der Material List
einer Schematic kann ein Material durch ein anderes ersetzt werden (im Speicher, mit Undo,
optional als neue `.litematic` speicherbar).

## Voraussetzungen

| | Version |
|---|---|
| Minecraft | 26.3 |
| Java (JDK) | 25 |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | ≥ 0.161.0+26.3 |
| MaLiLib | 0.30.x (≥ 0.30.2) |
| Litematica | 0.29.x (≥ 0.29.1) |

Gradle sucht das JDK 25 über die Toolchain automatisch. Wird es nicht gefunden, den Pfad
in `~/.gradle/gradle.properties` eintragen:

```properties
org.gradle.java.installations.paths=C:/Program Files/Java/jdk-25.0.4.1
```

## Bauen

```sh
./gradlew build
```

Die fertige JAR liegt in `build/libs/litematica-material-swap-mc26.3-<version>.jar`.
MaLiLib und Litematica werden nicht mit eingebündelt und müssen separat installiert sein.

## Im Dev-Client starten

```sh
./gradlew runClient
```

Startet Minecraft mit Fabric API, MaLiLib, Litematica und diesem Mod.

## Dokumentation

- [docs/RESEARCH.md](docs/RESEARCH.md) – Versionen, Litematica-Klassen, Einbindung
- [docs/DECISIONS.md](docs/DECISIONS.md) – Entscheidungen und offene Punkte

## Lizenz

LGPL-3.0
