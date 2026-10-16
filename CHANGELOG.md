# Changelog

Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionen nach [SemVer](https://semver.org/lang/de/).

## [1.0.0] – 2026-10-16

Erste Version. Minecraft 26.3, Fabric Loader ≥ 0.19.5, Fabric API ≥ 0.161.0+26.3, MaLiLib 0.30.x (≥ 0.30.2),
Litematica 0.29.x (≥ 0.29.1). Nur Client.

### Hinzugefügt
- **Material ersetzen** aus Litematicas Material List (Placement-Listen und Listen geladener Schematics): Button
  „Ersetzen“ pro Zeile, Screen mit durchsuchbarer Zielliste, Vorschau und Warnungen.
- Block-Properties werden übernommen (Slab-Typ, waterlogged, Treppen-Ausrichtung/-Hälfte/-Form …); strenge Prüfung,
  ohne Übernahme alle Blöcke mit Standard-Properties möglich. Luft als Ziel entfernt das Material.
- **Bereiche:** ganze Schematic, ausgewählte Regionen oder die aktuelle Litematica-Bereichsauswahl (mit korrekter
  Umrechnung für Rotation, Spiegelung und Sub-Regionen).
- **Filter** nach Property-Werten (z. B. nur obere Slabs) mit Anzahl je Wert und **feste Ziel-Properties**
  (z. B. `waterlogged=false`).
- **Batch-Ersetzung** mehrerer Materialien in einem Durchgang: Regel-Liste mit Sortieren, Vorlagen, Regelsätzen,
  Ketten-Erkennung (Modus „nur Originalzustand“ oder „nacheinander“), Vorschau mit Treffern und Material-Delta.
- **Rückgängig** (Historie pro Schematic, auch für Batches als ein Schritt) und **„Als neue Schematic speichern“**
  (`<name>_swapped`, Original nur nach Bestätigung überschreibbar).
- **Gespeicherte Regeln** global und pro Schematic-Datei (`config/lmswap_rules.json`, Format v2), optional beim Laden
  automatisch anwenden.
- **Config-Seite** im MaLiLib-Stil (Hotkey M,W, MaLiLib-Config-Umschalter, Mod Menu).
- Easy Place, Verifier und Hologramm arbeiten nach einer Ersetzung mit dem neuen Material.
- Deutsch und Englisch.

### Technik
- Ersetzung im Speicher der geladenen Schematic; Datei wird nur über „Speichern als“ geschrieben.
- 68 Unit-Tests (`fabric-loader-junit`), u. a. Weltkoordinaten-Umrechnung für alle Rotationen/Spiegelungen,
  Mixin-Anwendung auf Litematica, Vollständigkeit der Sprachdateien.
- GitHub Actions: Build bei jedem Push (JAR als Artefakt); beim Veröffentlichen eines Releases wird die JAR
  mit der Version aus dem Tag gebaut und ans Release gehängt.
