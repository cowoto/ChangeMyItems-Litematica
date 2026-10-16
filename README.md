# Litematica Material Swap

Client-Mod für Fabric (Minecraft 26.3), der [Litematica](https://github.com/sakura-ryoko/litematica) erweitert:
Materialien einer geladenen Schematic direkt aus der **Material List** austauschen – einzeln, gefiltert, nur in einem
Teilbereich oder als Batch. Hologramm, Verifier und Easy Place arbeiten danach mit dem neuen Material.

> Version 0.1.0 – siehe [CHANGELOG.md](CHANGELOG.md).

## Funktionen

- **Ersetzen aus der Material List** – Button „Ersetzen“ in jeder Zeile, Zielblock aus einer durchsuchbaren Liste.
- **Properties bleiben erhalten** – Slab oben/unten/doppelt, waterlogged, Treppen-Ausrichtung usw. werden übernommen.
- **Teilbereiche** – ganze Schematic, ausgewählte Regionen oder nur innerhalb deiner Litematica-Bereichsauswahl
  (Rotation, Spiegelung und Sub-Regionen des Placements werden berücksichtigt).
- **Filter und feste Properties** – z. B. nur obere Slabs ersetzen, oder bei allen ersetzten Blöcken `waterlogged=false`.
- **Batch-Ersetzung** – mehrere Materialien in einem Durchgang, mit Vorschau, Ketten-Erkennung und Material-Delta.
- **Rückgängig** – auch ein ganzer Batch ist ein Schritt.
- **Als neue Schematic speichern** – `<name>_swapped.litematic`, das Original bleibt unangetastet.
- **Regeln merken** – global oder pro Schematic, optional beim Laden automatisch anwenden; Batches als Regelsätze.
- **Konfiguration** im gewohnten MaLiLib-Stil, Deutsch und Englisch.

## Installation

Benötigt (alle in den `mods`-Ordner, Fabric-Profil):

| Mod | Version | Quelle |
|---|---|---|
| Minecraft | 26.3 | |
| [Fabric Loader](https://fabricmc.net/use/) | ≥ 0.19.5 | fabricmc.net |
| [Fabric API](https://modrinth.com/mod/fabric-api) | ≥ 0.161.0+26.3 | Modrinth |
| [MaLiLib](https://modrinth.com/mod/malilib) | 0.30.x, ≥ 0.30.2 | Modrinth |
| [Litematica](https://modrinth.com/mod/litematica) | 0.29.x, ≥ 0.29.1 | Modrinth |
| Litematica Material Swap | 0.1.0 | `litematica-material-swap-mc26.3-0.1.0.jar` |

Optional: [Mod Menu](https://modrinth.com/mod/modmenu) für den Config-Eintrag in der Mod-Liste.

Fehlt eine Abhängigkeit oder passt die Version nicht, zeigt Fabric beim Start eine Fehlermeldung mit der benötigten
Version. Der Mod läuft nur auf dem Client; auf Servern muss nichts installiert werden.

## Bedienung

### Ein Material ersetzen

1. Litematica-Menü (**M**) → Placement → **Material List** (oder Hotkey **M,L**).
2. In der Zeile des Materials auf **Ersetzen** klicken.
3. Zielblock wählen (Suche nach Name oder ID). „Luft“ entfernt das Material.
4. Optional:
   - **Block-Properties übernehmen** (an: nur Blöcke mit gleichen Properties wie Slab → Slab; aus: alle Blöcke,
     Standard-Properties),
   - **Bereich** rechts: ganze Schematic, ausgewählte Regionen oder aktuelle Bereichsauswahl,
   - **▸ Filter**: nur bestimmte Property-Werte (mit Anzahl je Wert),
   - **▸ Ziel-Properties fest setzen**,
   - als Regel für diese Schematic oder global merken.
5. Vorschau prüfen („N Blöcke werden ersetzt“ + Warnungen) → **Ersetzen**.


### Batch-Ersetzung

1. Material List → **Batch-Ersetzung**.
2. **+ Regel hinzufügen**: Quellmaterial wählen, dann wie oben einstellen → **Übernehmen**. Gespeicherte Regeln lassen
   sich rechts per „+“ übernehmen, Regelsätze laden.
3. Reihenfolge mit ↑ ↓ oder **Shift+↑/↓** – die erste passende Regel gewinnt.
4. Rechts: gemeinsamer **Batch-Bereich** und Ketten-Modus (**nur Originalzustand** = Standard, oder **nacheinander
   anwenden**).
5. Vorschau unten: Treffer je Regel, Warnungen (Ketten, überlappende Filter), Änderung der Material List →
   **Ausführen**.


### Rückgängig und Speichern

- **Letzte Ersetzung rückgängig** (Material List) stellt den Zustand vor der letzten Ersetzung bzw. dem letzten Batch
  exakt wieder her (bis zu 20 Schritte pro Schematic).
- **Als neue Schematic speichern** schreibt `<name>_swapped.litematic` neben das Original. Das Original wird nur nach
  ausdrücklicher Bestätigung überschrieben.
- Ohne Speichern gelten Ersetzungen nur bis zum Neuladen der Schematic.


### Konfiguration

Hotkey **M,W**, MaLiLibs Config-Umschalter (z. B. aus Litematicas Config **M,C**) oder Mod Menu.

| Option | Standard | Bedeutung |
|---|---|---|
| Gespeicherte Regeln beim Laden anwenden | aus | Beim Laden/Neuladen einer Schematic ihre Regeln und die globalen Regeln anwenden |
| Konfiguration öffnen | M,W | Hotkey für diese Seite |

Dateien: `config/lmswap.json` (Optionen), `config/lmswap_rules.json` (Regeln und Regelsätze; ältere Dateien werden
automatisch migriert, Sicherung `lmswap_rules.v1.bak.json`).

## Bekannte Einschränkungen

- **Litematica 0.29.x und MaLiLib 0.30.x** – der Mod greift per Mixin in Litematica ein; andere Versionen lehnt Fabric
  beim Start ab.
- **Material-List-Einträge sind Items**, nicht Block-States. „Nur diesen State ersetzen“ geht über den Filter im
  Ersetzen-Screen.
- **Strenge Property-Prüfung** bei „Properties übernehmen“: Slab → Vollblock oder Fackel/Wandfackel nur mit
  ausgeschalteter Übernahme.
- **Ersetzungen betreffen die Schematic**, nicht nur ein Placement – nutzen mehrere Placements dieselbe Schematic,
  ändern sich alle (es wird gewarnt).
- **Bereichsauswahl** nur in der Material List eines Placements; Filter/Overrides beziehen sich auf die
  Schematic-Ausrichtung (z. B. `facing`), nicht auf das gedrehte Placement.
- **Material List aus dem Dateibrowser** („Load Schematics“ → „Material List“), Bereichsanalyse und Custom-Listen:
  kein Ersetzen (dort gibt es keine geladene Schematic).
- **Rückgängig** geht verloren beim Entladen oder „Reload“ der Schematic; Änderungen mit Litematicas eigenem
  Edit-Modus zwischen Ersetzen und Rückgängig werden überschrieben.
- **Regeln sind an den Dateipfad gebunden** – nach Umbenennen/Verschieben gelten nur noch globale Regeln.
- **Batch:** höchstens 63 Regeln; kein Drag & Drop (Pfeile bzw. Shift+↑/↓). Sehr große Batches (Millionen Blöcke)
  verursachen einen kurzen Hänger.
- Version 0.1.0 ist im Dev-Client entwickelt und per Unit-Tests geprüft; Rückmeldungen über die Issues sind willkommen.

## Lizenz

Litematica Material Swap steht unter der **GNU Lesser General Public License v3.0** ([LICENSE](LICENSE), auf Basis
der GPL v3: [COPYING](COPYING)).

Der Mod ist ein eigenständiges Add-on und **enthält keinen Code aus Litematica oder MaLiLib**. Beide werden zur
Laufzeit benötigt, aber nicht mitgeliefert; sie stehen selbst unter der LGPL-3.0 (© masa, sakura-ryoko und
Mitwirkende) und müssen separat installiert werden. Litematica und MaLiLib sind nicht mit diesem Projekt verbunden.

## Entwicklung

Voraussetzung: JDK 25. Gradle findet es über die Toolchain; sonst in `~/.gradle/gradle.properties`
`org.gradle.java.installations.paths=<Pfad zum JDK 25>` eintragen.

```sh
./gradlew build      # baut und führt 68 Unit-Tests aus -> build/libs/litematica-material-swap-mc26.3-0.1.0.jar
./gradlew runClient  # Dev-Client mit Fabric API, MaLiLib, Litematica und diesem Mod
```

Jeder Push baut über GitHub Actions ([.github/workflows/build.yml](.github/workflows/build.yml)); die JAR liegt als
Artefakt am Workflow-Lauf.
