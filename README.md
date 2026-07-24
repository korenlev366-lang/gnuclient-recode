# GNUClient (Forge recode)

Minecraft 1.8.9 Forge client mod — Essential Loom + mixins + Java ClickGUI.

**ViaForgePlus is compiled into this jar** (sources from sibling `../ViaForgePlus`, plus its `libs/*.jar`). Keep that checkout next to `gnuclient recode` when building.

## build

```bash
./gradlew build -x test -Dorg.gradle.jvmargs="-Xmx8g"
```

Output jar: `build/libs/gnuclient-<version>.jar` — one inject (gnuclient + ViaForgePlus).

## Dev client

```bash
./gradlew runClient
```

## Scripts

Example bare-body Java scripts live in [`scripts/`](scripts/). Copy them into
`~/.config/gnuclient/scripts/` and reload with **Right Shift + R** (or restart).
See [`scripts/README.md`](scripts/README.md) for the ultra-custom API:
cross-module control, `draw` / `hud`, `shared` bus, script chat commands, and hooks.

DC
https://discord.gg/PZBFs4EJCF
YT
https://www.youtube.com/@Kinglxss
