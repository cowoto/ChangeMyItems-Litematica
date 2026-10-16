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

## Bedienung

Alles läuft über Litematicas **Material List** (eines Placements oder einer geladenen Schematic).

**Ersetzen** (Button in jeder Zeile) öffnet „Material ersetzen“:
- Zielblock in der durchsuchbaren Liste wählen („Luft“ entfernt das Material).
- „Block-Properties übernehmen“: an = nur Blöcke mit gleichen Properties (Slab → Slab, Treppe → Treppe),
  aus = alle Blöcke, das Ziel bekommt seine Standardwerte.
- **Bereich** (rechts oben): ganze Schematic, ausgewählte Regionen oder die aktuelle Litematica-Bereichsauswahl
  (nur in der Material List eines Placements; Rotation/Spiegelung werden berücksichtigt).
- **▸ Filter**: nur Blöcke mit bestimmten Property-Werten ersetzen, z. B. nur `type: top`; jeder Wert zeigt seine Anzahl.
- **▸ Ziel-Properties fest setzen**: Properties, die jeder ersetzte Block bekommt, z. B. `waterlogged: false`.
- Vorschau mit Anzahl und Warnungen; optional als Regel für diese Schematic oder global merken.

**Batch-Ersetzung** (unten in der Material List): mehrere Regeln auf einmal, in Reihenfolge („erste passende Regel
gewinnt“). Regeln hinzufügen (Quelle wählen → wie oben), mit ↑ ↓ bzw. Shift+↑/↓ sortieren, bearbeiten, löschen;
gespeicherte Regeln als Vorlage übernehmen; als Regelsatz speichern und wieder laden. Gemeinsamer Bereich für den Batch,
pro Regel überschreibbar. Ketten (A → B, B → C) werden erkannt; Modus „nur Originalzustand“ (Standard) oder
„nacheinander anwenden“. Vorschau mit Treffern je Regel und Material-Delta. Ein Batch ist **ein** Rückgängig-Schritt.

**Letzte Ersetzung rückgängig** und **Als neue Schematic speichern** (`<name>_swapped`, Original wird nur nach
ausdrücklicher Bestätigung überschrieben). Ohne Speichern gelten Ersetzungen nur bis zum Neuladen der Schematic.

## Konfiguration

- Config-Seite: Hotkey **M,W**, über MaLiLibs Config-Umschalter oder Mod Menu.
- `config/lmswap.json` – Optionen (z. B. „Gespeicherte Regeln beim Laden anwenden“, Standard aus).
- `config/lmswap_rules.json` – gespeicherte Regeln (global, pro Schematic-Datei) und Regelsätze, Format-Version 2.
  Eine Version-1-Datei wird beim ersten Start automatisch migriert (Sicherung: `lmswap_rules.v1.bak.json`).
- Beim automatischen Anwenden werden Regeln mit Bereich „Bereichsauswahl“ übersprungen (Hinweis im Spiel).

## Tests

`./gradlew build` führt 68 Unit-Tests aus (`fabric-loader-junit`, Minecraft wird im Test gebootstrapt). Fälle und
Ergebnisse, auch die nur im Spiel prüfbaren: [docs/TESTS.md](docs/TESTS.md).

## Dokumentation

- [docs/RESEARCH.md](docs/RESEARCH.md) – Versionen, Litematica-Klassen, Einbindung
- [docs/DECISIONS.md](docs/DECISIONS.md) – Entscheidungen und offene Punkte
- [docs/TESTS.md](docs/TESTS.md) – Randfälle, Testfälle, manuelle Prüfschritte

## Lizenz

LGPL-3.0
